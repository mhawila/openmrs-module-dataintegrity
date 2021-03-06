/**
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */

package org.openmrs.module.dataintegrity.web.controller;

import au.com.bytecode.opencsv.CSVWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.messagesource.MessageSourceService;
import org.openmrs.module.dataintegrity.DataIntegrityConstants;
import org.openmrs.module.dataintegrity.DataIntegrityService;
import org.openmrs.module.dataintegrity.IntegrityCheck;
import org.openmrs.module.dataintegrity.IntegrityCheckColumn;
import org.openmrs.module.dataintegrity.IntegrityCheckResult;
import org.openmrs.util.OpenmrsUtil;
import org.openmrs.web.WebConstants;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.WebRequest;

@Controller
public class IntegrityCheckController {
	private static final String EDIT_VIEW = "/module/dataintegrity/editCheck";
	private static final String VIEW_VIEW = "/module/dataintegrity/viewCheck";
	private static final String LIST_VIEW = "/module/dataintegrity/listChecks";
	private static final String SUCCESS_VIEW = "redirect:list.htm";
	
	private final Log log = LogFactory.getLog(this.getClass());
	
	private DataIntegrityService getDataIntegrityService() {
        return (DataIntegrityService)Context.getService(DataIntegrityService.class);
    }

	/**
	 * handle initial edit request
	 *
	 * @param checkId
	 * @param modelMap
	 * @return
	 * @throws Exception
	 */
	@RequestMapping(value="/module/dataintegrity/edit.htm")
	public String editIntegrityCheck(@RequestParam(value="checkId", required=true) Integer checkId, ModelMap modelMap) {
		modelMap.put("check", getDataIntegrityService().getIntegrityCheck(checkId));
		modelMap.put("columnDatatypes", DataIntegrityConstants.COLUMN_DATATYPES);
        return EDIT_VIEW;
	}

	@RequestMapping(value="/module/dataintegrity/view.htm")
	public String viewCheck(@RequestParam(value="checkId", required=true) Integer checkId, ModelMap modelMap) {
		DataIntegrityService service = Context.getService(DataIntegrityService.class);
		modelMap.put("check", service.getIntegrityCheck(checkId));
		return VIEW_VIEW;
	}

	@RequestMapping(value="/module/dataintegrity/list.htm")
	public String listChecks(ModelMap modelMap) {
        List<IntegrityCheck> checks = new ArrayList<IntegrityCheck>();
        if (Context.isAuthenticated()) {
        	checks = getDataIntegrityService().getAllIntegrityChecks(); 
        }
		modelMap.put("checks", checks);
		
        return LIST_VIEW;
    }

	@RequestMapping(value="/module/dataintegrity/new.htm")
	public String newCheck(ModelMap modelMap) {
		modelMap.put("check", new IntegrityCheck());
		modelMap.put("columnDatatypes", DataIntegrityConstants.COLUMN_DATATYPES);
        return EDIT_VIEW;
	}

	@RequestMapping(value="/module/dataintegrity/duplicate.htm")
	public String copyCheck(@RequestParam(value="checkId", required=true) Integer checkId, ModelMap modelMap) {
		// create a copy ...
		IntegrityCheck clone = getDataIntegrityService().getIntegrityCheck(checkId).clone(false);
		clone.setName(null);
		clone.setDescription(null);
		
		modelMap.put("check", clone);
		modelMap.put("columnDatatypes", DataIntegrityConstants.COLUMN_DATATYPES);
        return EDIT_VIEW;
	}

	@RequestMapping(value="/module/dataintegrity/download.htm")
	public void downloadCSV(
		@RequestParam(value="checkId", required=true) Integer checkId, 
		HttpServletResponse response) {
		
		// get the check
		IntegrityCheck check = getDataIntegrityService().getIntegrityCheck(checkId);
		if (check == null)
			return;

		String filename = check.getName().toLowerCase().replaceAll(" ", "-").concat(".csv");
		
		response.setContentType("application/vnd.ms-excel");
		response.setHeader("Content-Disposition","attachment; filename=\"" + filename + "\"");
		
		try {
			// open a new CSVWriter connected to the response
			OutputStreamWriter out = new OutputStreamWriter(response.getOutputStream());
			CSVWriter writer = new CSVWriter(out, ',');
			
			// get column names and display names
			List<String> columns = new ArrayList<String>();
			List<String> titles = new ArrayList<String>();
			for (IntegrityCheckColumn column : check.getResultsColumns()) {
				columns.add(column.getName());
				titles.add(column.getDisplayName());
			}

			// write out the columns
			writer.writeNext(columns.toArray(new String[]{}));

			// iterate through results
			for (IntegrityCheckResult result : check.getIntegrityCheckResults()) {
				if (OpenmrsUtil.nullSafeEquals(result.getStatus(), DataIntegrityConstants.RESULT_STATUS_NEW)) {
					List<String> row = new ArrayList<String>();
					for (String col : columns)
						row.add(result.getData().get(col).toString());

					// write out the row
					writer.writeNext(row.toArray(new String[]{}));
				}
			}
			
			// close the writer
			writer.close();

			// flush it down
			response.flushBuffer();
			
		} catch (IOException ex) {
			log.info("Error writing integrity check #" + checkId + " to output stream", ex);
			throw new RuntimeException("IOError writing file to output stream", ex);
		}

	}	
	
	@RequestMapping(value="/module/dataintegrity/delete.htm")
	public String deleteCheck(@RequestParam(value="checkId", required=true) Integer checkId, WebRequest request) {
		DataIntegrityService service = Context.getService(DataIntegrityService.class);
		MessageSourceService mss = Context.getMessageSourceService();

		String checkTitle = mss.getMessage("dataintegrity.delete.checktitle");
		String deleted = mss.getMessage("dataintegrity.delete.deleted");
		String notDeleted = mss.getMessage("dataintegrity.delete.notdeleted");
		String notFound = mss.getMessage("dataintegrity.delete.notfound");
		String success = "";
		String error = "";
		
		IntegrityCheck check = service.getIntegrityCheck(checkId);
		if (check != null)
			try {
				String name = check.getName();
				service.deleteIntegrityCheck(check);
				success = checkTitle + " #" + checkId + " (" + name + ") " + deleted;
			} catch (Exception e) {
				error = checkTitle + " #" + checkId + " " + notDeleted + ": " + e.getMessage();
			}
		else
			error = checkTitle + " #" + checkId + " " + notDeleted + ": " + notFound;
		
		if (!success.equals(""))
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, success, WebRequest.SCOPE_SESSION);
		if (!error.equals(""))
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, error, WebRequest.SCOPE_SESSION);

		return SUCCESS_VIEW;
	}

	@RequestMapping(value="/module/dataintegrity/retire.htm")
	public String retireCheck(
			@RequestParam(value="checkId", required=true) Integer checkId, 
			@RequestParam(value="retireReason", required=true) String retireReason, 
			WebRequest request) {
		DataIntegrityService service = Context.getService(DataIntegrityService.class);
		MessageSourceService mss = Context.getMessageSourceService();

		String checkTitle = mss.getMessage("dataintegrity.delete.checktitle");
		String retired = mss.getMessage("general.retired");
		String notRetired = mss.getMessage("general.cannot.retire");
		String notFound = mss.getMessage("dataintegrity.delete.notfound");
		String success = "";
		String error = "";
		
		IntegrityCheck check = service.getIntegrityCheck(checkId);
		if (check != null)
			try {
				String name = check.getName();
				service.retireIntegrityCheck(check, retireReason);
				success = checkTitle + " #" + checkId + " (" + name + ") " + retired;
			} catch (Exception e) {
				error = checkTitle + " #" + checkId + " " + notRetired + ": " + e.getMessage();
			}
		else
			error = checkTitle + " #" + checkId + " " + notRetired + ": " + notFound;
		
		if (!success.equals(""))
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, success, WebRequest.SCOPE_SESSION);
		if (!error.equals(""))
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, error, WebRequest.SCOPE_SESSION);

		return SUCCESS_VIEW;
	}
	
	@RequestMapping(value="/module/dataintegrity/unretire.htm")
	public String unretireCheck(
			@RequestParam(value="checkId", required=true) Integer checkId,
			WebRequest request) {
		DataIntegrityService service = Context.getService(DataIntegrityService.class);
		MessageSourceService mss = Context.getMessageSourceService();

		String checkTitle = mss.getMessage("dataintegrity.delete.checktitle");
		String unretired = mss.getMessage("general.unretired");
		String notUnretired = mss.getMessage("general.cannot.unretire");
		String notFound = mss.getMessage("dataintegrity.delete.notfound");
		String success = "";
		String error = "";
		
		IntegrityCheck check = service.getIntegrityCheck(checkId);
		if (check != null)
			try {
				String name = check.getName();
				service.unretireIntegrityCheck(check);
				success = checkTitle + " #" + checkId + " (" + name + ") " + unretired;
			} catch (Exception e) {
				error = checkTitle + " #" + checkId + " " + notUnretired + ": " + e.getMessage();
			}
		else
			error = checkTitle + " #" + checkId + " " + notUnretired + ": " + notFound;
		
		if (!success.equals(""))
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, success, WebRequest.SCOPE_SESSION);
		if (!error.equals(""))
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, error, WebRequest.SCOPE_SESSION);

		return SUCCESS_VIEW;
	}
	
	@RequestMapping(value="/module/dataintegrity/save.htm")
	public String saveCheck(WebRequest request,
			@RequestParam(value="checkId", required=false) Integer checkId,
			@RequestParam(value="name", required=true) String name,
			@RequestParam(value="description", required=true) String description,
			@RequestParam(value="checkLanguage", required=false) String checkLanguage,
			@RequestParam(value="checkCode", required=false) String checkCode,
			@RequestParam(value="failureType", required=false) String failureType,
			@RequestParam(value="failureOperator", required=false) String failureOperator,
			@RequestParam(value="failureThreshold", required=false) Integer failureThreshold,
			@RequestParam(value="useTotal", required=false) Boolean useTotal,
			@RequestParam(value="totalLanguage", required=false) String totalLanguage,
			@RequestParam(value="totalCode", required=false) String totalCode,
			@RequestParam(value="useDiscoveryForResults", required=false) Boolean useDiscoveryForResults,
			@RequestParam(value="resultsLanguage", required=false) String resultsLanguage,
			@RequestParam(value="resultsCode", required=false) String resultsCode,
			@RequestParam(value="columns[]", required=false) String[] columns) {

		DataIntegrityService service = (DataIntegrityService)Context.getService(DataIntegrityService.class);
		MessageSourceService mss = Context.getMessageSourceService();
		
		try {
			// validate code fields
			validateCode(checkCode);
			validateCode(totalCode);
			validateCode(resultsCode);
			
			// set booleans if they didn't come through
			if (useTotal == null)
				useTotal = false;
			if (useDiscoveryForResults == null)
				useDiscoveryForResults = false;

			// get Integrity Check if it exists
			IntegrityCheck check = service.getIntegrityCheck(checkId);
			if (check == null) {
				// create integrity check
				check = new IntegrityCheck();
			}

			// set all the other fields (ignore id if it was not found)
			check.setName(name);
			check.setDescription(description);
			check.setCheckCode(checkCode);
			check.setCheckLanguage(checkLanguage);
			check.setFailureType(failureType);
			check.setFailureThreshold(failureThreshold.toString());
			check.setFailureOperator(failureOperator);
			check.setTotalLanguage(useTotal ? totalLanguage : null);
			check.setTotalCode(useTotal ? totalCode : null);
			check.setResultsLanguage(useDiscoveryForResults ? null : resultsLanguage);
			check.setResultsCode(useDiscoveryForResults ? null : resultsCode);

			// replace existing columns with new ones
			check.updateColumns(generateColumns(columns));
					
			service.saveIntegrityCheck(check);

			// TODO figure out how to send this value back in an annotated controller :-/
			String success = name + " " + mss.getMessage("dataintegrity.addeditCheck.saved");
			request.setAttribute(WebConstants.OPENMRS_MSG_ATTR, success, WebRequest.SCOPE_SESSION);
			
			return SUCCESS_VIEW;
			
		} catch (Exception e) {
			StringBuilder sb = new StringBuilder();
			sb.append(mss.getMessage("dataintegrity.edit.failed"));
			sb.append(": ");
			sb.append(name);
			sb.append(", Message: ");
			sb.append(e.getMessage());

			log.warn(sb.toString(), e);
			request.setAttribute(WebConstants.OPENMRS_ERROR_ATTR, sb.toString(), WebRequest.SCOPE_SESSION);
			
			// TODO retain the data from the form so it can be repopulated
			return EDIT_VIEW;
		}
	}

	private void validateCode(String code) throws Exception {
		// TODO make this better
		List<String> tokens = Arrays.asList(code.toLowerCase().split(" "));
		if (tokens.contains("delete") || tokens.contains("update") || tokens.contains("insert"))
			throw new Exception("Code has potential to modify database, so it is not allowed.");
	}

	private List<IntegrityCheckColumn> generateColumns(String[] columns) {
		List<IntegrityCheckColumn> results = new ArrayList<IntegrityCheckColumn>();
		Integer index = 1;
		for(String column: columns) {
			// deserialize the column
			String[] items = column.split(":", 7);
			
			// properties should be in order [id:show:uid:name:display]
			IntegrityCheckColumn c = new IntegrityCheckColumn();
			try {
				c.setColumnId(StringUtils.hasText(items[0]) ? Integer.parseInt(items[0]) : null);
			} catch (NumberFormatException e) {
				log.warn("could not interpret IntegrityCheckColumn id of " 
						+ items[0] + " as an Integer; defaulting to null.");
				c.setColumnId(null);
			}
			c.setShowInResults(OpenmrsUtil.nullSafeEquals(items[1], "true"));
			c.setUsedInUid(OpenmrsUtil.nullSafeEquals(items[2], "true"));
			c.setName(StringUtils.hasText(items[3]) ? items[3] : null);
			c.setDatatype(StringUtils.hasText(items[4]) ? items[4] : null);
			c.setUuid(StringUtils.hasText(items[5]) ? items[5] : null);
			c.setDisplayName(StringUtils.hasText(items[6]) ? items[6] : null);
			c.setColumnIndex(index++);
			
			// convert "null" to null in UUID
			if (OpenmrsUtil.nullSafeEquals(c.getUuid(), "null"))
				c.setUuid(null);
			
			// if the name is null, there's a problem ... probaby a dud
			if (c.getName() != null)
				results.add(c);
		}
		return results;
	}

}
