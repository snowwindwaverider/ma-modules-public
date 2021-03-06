package com.serotonin.m2m2.dataImport;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import au.com.bytecode.opencsv.CSVReader;

import com.serotonin.m2m2.Common;
import com.serotonin.m2m2.db.dao.DaoRegistry;
import com.serotonin.m2m2.db.dao.DataPointDao;
import com.serotonin.m2m2.db.dao.EnhancedPointValueDao;
import com.serotonin.m2m2.db.dao.PointValueDao;
import com.serotonin.m2m2.db.dao.SystemSettingsDao;
import com.serotonin.m2m2.i18n.TranslatableException;
import com.serotonin.m2m2.i18n.TranslatableMessage;
import com.serotonin.m2m2.i18n.Translations;
import com.serotonin.m2m2.rt.dataImage.DataPointRT;
import com.serotonin.m2m2.rt.dataImage.PointValueTime;
import com.serotonin.m2m2.rt.dataImage.types.DataValue;
import com.serotonin.m2m2.vo.DataPointVO;
import com.serotonin.m2m2.vo.User;
import com.serotonin.m2m2.vo.dataSource.DataSourceVO;
import com.serotonin.m2m2.vo.export.ExportCsvStreamer;
import com.serotonin.m2m2.vo.permission.PermissionException;
import com.serotonin.m2m2.vo.permission.Permissions;
import com.serotonin.m2m2.web.mvc.controller.FileUploadController;



public class DataImportController extends FileUploadController {
	
	
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.web.mvc.controller.FileUploadController#checkPermission(javax.servlet.http.HttpServletRequest)
	 */
	@Override
	protected void checkPermission(HttpServletRequest request)
			throws PermissionException {

		User user = Common.getUser(request);
        if(!Permissions.hasPermission(user, SystemSettingsDao.getValue(DataImportPermissionDefinition.PERMISSION)))
        		throw new PermissionException("No " + DataImportPermissionDefinition.PERMISSION  + " permission", user);
		
	}
	/* (non-Javadoc)
	 * @see com.serotonin.m2m2.web.mvc.controller.FileUploadController#parseFile(java.io.InputStream, java.util.Map, com.serotonin.m2m2.i18n.Translations, javax.servlet.http.HttpServletRequest)
	 */
	@Override
	protected void parseFile(InputStream input, Map<String, Object> model,
			Translations translations, HttpServletRequest request) {
		
		//Setup for error tracking
		List<String> errorMessages = new ArrayList<String>();
		model.put("errorMessages", errorMessages);
		
		CSVReader csvReader = new CSVReader(new InputStreamReader(input));
		try {
            importCsv(csvReader, model, translations, errorMessages);
        }
        catch (Exception e) {
        	if(e instanceof TranslatableException)
        		errorMessages.add(((TranslatableException)e).getTranslatableMessage().translate(translations));
        	else
        		errorMessages.add(e.getMessage());
        }finally{
        	model.put("hasImportErrors", errorMessages.size() > 0);
        	try {
				csvReader.close();
			} catch (IOException e) {
				errorMessages.add(e.getMessage());
			}
        }
	}
 

    /**
     * The file needs to be in the format:
     * 
     * Data Point XID, Device Name, Point name, Time, Value, Rendered, Annotation, Modify(Not used yet)
     * 
     * 
     * @param reader
     * @param model
     * @return
     * @throws IOException
     * @throws TranslatableException
     */
    private void importCsv(CSVReader csvReader, Map<String, Object> model, Translations translations, List<String> errorMessages) throws IOException, TranslatableException {
    	
        DataPointDao dataPointDao = new DataPointDao();
        PointValueDao pointValueDao = Common.databaseProxy.newPointValueDao();
        
        int rowErrors = 0;
        
        // Basic validation of header
        String[] nextLine = csvReader.readNext();
        if (nextLine == null){
        	errorMessages.add(new TranslatableMessage("dataImport.import.noData").translate(translations));
        	return;
        }
        if (nextLine.length != ExportCsvStreamer.columns){
        	errorMessages.add(new TranslatableMessage("dataImport.import.invalidHeaders", nextLine.length, ExportCsvStreamer.columns).translate(translations));
        	return;
        }

        //Map of XIDs to non-running data points
        Map<String, DataPointVO> voMap = new HashMap<String, DataPointVO>();
        //Map of XIDs to running data points
        Map<String, DataPointRT> rtMap = new HashMap<String, DataPointRT>();
        
        //Read in all the rows
        int row = 1;
        String xid;
        DataPointVO vo;
        DataPointRT rt;
        long time;
        DataValue value;
        PointValueTime pvt;

        while ((nextLine = csvReader.readNext()) != null) {

        	if(nextLine.length != ExportCsvStreamer.columns){
        		errorMessages.add(new TranslatableMessage("dataImport.import.invalidLength", row).translate(translations));
        		continue;
        	}
        	
        	//Check XID
        	xid = nextLine[0];
        	if (StringUtils.isBlank(xid)){
        		errorMessages.add(new TranslatableMessage("dataImport.import.badXid", xid, row).translate(translations));
        		rowErrors++;
        		continue;
        	}
        	//First Check to see if we already have a point
        	vo = voMap.get(xid);
        	rt = rtMap.get(xid);
        	
        	//We will always have the vo in the map but the RT may be null if the point isn't running
        	if(vo == null){
        		vo = dataPointDao.getDataPoint(xid);
	            if (vo == null){
	            	errorMessages.add(new TranslatableMessage("dataImport.import.xidNotFound", xid, row).translate(translations));
	            	rowErrors++;
	            	continue;
	            }
	        	rt = Common.runtimeManager.getDataPoint(vo.getId());
	
        		rtMap.put(xid, rt);
        		voMap.put(xid, vo);
        	}
        	
        	//Going to insert some data
            time = ExportCsvStreamer.dtf.parseDateTime(nextLine[3]).getMillis();
            value = DataValue.stringToValue(nextLine[4], vo.getPointLocator().getDataTypeId());
            pvt = new PointValueTime(value, time);
        	
        	if(rt == null){
        		//Insert Via DAO
        		if (pointValueDao instanceof EnhancedPointValueDao) {
                    DataSourceVO<?> ds = getDataSource(vo.getDataSourceId());
                    ((EnhancedPointValueDao) pointValueDao).savePointValueAsync(vo, ds, pvt,null);
                } else {
                    pointValueDao.savePointValueAsync(vo.getId(), pvt, null);
                }
        	}else{
        		//Insert Via RT
        		rt.savePointValueDirectToCache(pvt, null, true, true);
        	}

        	row++;
        }
 
        //Setup results
        model.put("rowsImported", row-2);
        model.put("rowsWithErrors", rowErrors);
        
    }
    
    Map<Integer, DataSourceVO<?>> cachedDataSources = new HashMap<>();
    
    DataSourceVO<?> getDataSource(int dataSourceId) {
        DataSourceVO<?> ds = cachedDataSources.get(dataSourceId);
        if (ds == null) {
            ds = DaoRegistry.dataSourceDao.get(dataSourceId);
            if (ds != null)
                cachedDataSources.put(dataSourceId, ds);
        }
        return ds;
    }
}
