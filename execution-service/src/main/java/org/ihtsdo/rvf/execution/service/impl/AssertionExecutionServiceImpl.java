package org.ihtsdo.rvf.execution.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.dbcp.BasicDataSource;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.ihtsdo.rvf.entity.Assertion;
import org.ihtsdo.rvf.entity.AssertionTest;
import org.ihtsdo.rvf.entity.ExecutionCommand;
import org.ihtsdo.rvf.entity.Test;
import org.ihtsdo.rvf.execution.service.AssertionExecutionService;
import org.ihtsdo.rvf.execution.service.util.TestRunItem;
import org.ihtsdo.rvf.helper.Configuration;
import org.ihtsdo.rvf.service.AssertionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Enumeration;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * An implementation of the {@link org.ihtsdo.rvf.execution.service.AssertionExecutionService}
 */
@Service
public class AssertionExecutionServiceImpl implements AssertionExecutionService {

    @Autowired
    AssertionService assertionService;
    @Resource(name = "dataSource")
    BasicDataSource dataSource;
    @Resource(name = "snomedDataSource")
    BasicDataSource snomedDataSource;
    String qaResulTableName;
    String assertionIdColumnName;
    String assertionNameColumnName;
    String assertionDetailsColumnName;
    ObjectMapper mapper = new ObjectMapper();
    String deltaTableSuffix = "d";
    String snapshotTableSuffix = "s";
    String fullTableSuffix = "f";

    private final Logger logger = LoggerFactory.getLogger(AssertionExecutionServiceImpl.class);
    private PreparedStatement insertStatement;

    public void initialiseResultTable(Long executionId) {
        String createSQLString = "CREATE TABLE IF NOT EXISTS " + qaResulTableName + "(RUN_ID BIGINT, ASSERTION_ID BIGINT, " +
                " ASSERTION_TEXT VARCHAR(255), DETAILS VARCHAR(255))";
        String insertSQL = "insert into " + qaResulTableName + " (run_id, assertion_id, assertion_text, details) values (?, ?, ?, ?)";
        try {
            dataSource.getConnection().createStatement().execute(createSQLString);
            insertStatement = dataSource.getConnection().prepareStatement(insertSQL);
        }
        catch (SQLException e) {
            logger.error("Error initialising Results table. Nested exception is : " + e.fillInStackTrace());
        }
    }

    @Override
    public TestRunItem executeAssertionTest(AssertionTest assertionTest, Long executionId,
                                            String prospectiveReleaseVersion, String previousReleaseVersion) {

        return executeTest(assertionTest.getTest(), executionId, prospectiveReleaseVersion, previousReleaseVersion);
    }

    @Override
    public Collection<TestRunItem> executeAssertionTests(Collection<AssertionTest> assertionTests, Long executionId,
                                                         String prospectiveReleaseVersion, String previousReleaseVersion) {
        Collection<TestRunItem> items = new ArrayList<>();
        for(AssertionTest at: assertionTests){
            items.add(executeAssertionTest(at, executionId, prospectiveReleaseVersion, previousReleaseVersion));
        }

        return items;
    }

    @Override
    public Collection<TestRunItem> executeAssertion(Assertion assertion, Long executionId,
                                                    String prospectiveReleaseVersion, String previousReleaseVersion) {

        Collection<TestRunItem> runItems = new ArrayList<>();

        //get tests for given assertion
        for(Test test: assertionService.getTests(assertion))
        {
            runItems.add(executeTest(test, executionId, prospectiveReleaseVersion, previousReleaseVersion));
        }

        return runItems;
    }

    @Override
    public Collection<TestRunItem> executeAssertions(Collection<Assertion> assertions, Long executionId,
                                                     String prospectiveReleaseVersion, String previousReleaseVersion) {
        Collection<TestRunItem> items = new ArrayList<>();
        for(Assertion assertion : assertions){
            items.addAll(executeAssertion(assertion, executionId, prospectiveReleaseVersion, previousReleaseVersion));
        }

        return items;
    }

    @Override
    public TestRunItem executeTest(Test test, Long executionId, String prospectiveReleaseVersion, String previousReleaseVersion) {

        logger.info("Started execution id = " + executionId);
        Calendar startTime = Calendar.getInstance();
        // initialise result table that stores results
        initialiseResultTable(executionId);
        // set prospective version as default schema to use since SQL has calls that do not specify schema name
        snomedDataSource.setDefaultCatalog(prospectiveReleaseVersion);

        TestRunItem runItem = new TestRunItem();
        runItem.setTestTime(Calendar.getInstance().getTime());
        runItem.setExecutionId(String.valueOf(executionId));
        runItem.setTestType(test.getType().name());

        // get command from test and validate the included command object
        ExecutionCommand command = test.getCommand();
        if(command != null)
        {
            // get test configuration
            Configuration testConfiguration = test.getCommand().getConfiguration();
            if(command.validate(test.getType(), testConfiguration))
            {
                // execute sql and get result
                try
                {
                    String[] parts = {""};


                    if(command.getStatements().size() == 0)
                    {
                        String sql = command.getTemplate();
                        parts = sql.split(";");
                    }
                    else{
                        parts = command.getStatements().toArray(new String[command.getStatements().size()]);
                    }
                    // parse sql to get select statement
                    String selectSQL = null;
                    for(String part: parts)
                    {
                        // remove all SQL comments - //TODO might throw errors for -- style comments
                        Pattern commentPattern = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
                        part = commentPattern.matcher(part).replaceAll("");

                        // replace all substitutions for exec
                        part = part.replaceAll("<RUNID>", String.valueOf(executionId));
                        part = part.replaceAll("<ASSERTIONUUID>", String.valueOf(test.getId()));
                        part = part.replaceAll("<ASSERTIONTEXT>", test.getName());
                        part = part.replaceAll("qa_result_table", qaResulTableName);
                        part = part.replaceAll("<PROSPECTIVE>", prospectiveReleaseVersion);
                        part = part.replaceAll("<PREVIOUS>", previousReleaseVersion);
                        part = part.replaceAll("<DELTA>", deltaTableSuffix);
                        part = part.replaceAll("<SNAPSHOT>", snapshotTableSuffix);
                        part = part.replaceAll("<FULL>", fullTableSuffix);

                        for(String key : testConfiguration.getKeys())
                        {
                            logger.info("key : value " + key + " : " + testConfiguration.getValue(key));
                            part = part.replaceAll(key, testConfiguration.getValue(key));
                        }

                        if(part.startsWith("select")){
                            logger.info("Set select query :" + part);
                            selectSQL = part;

                            PreparedStatement preparedStatement = snomedDataSource.getConnection().prepareStatement(selectSQL);
                            logger.info("Created statement");
                            ResultSet execResult = preparedStatement.executeQuery();
                            logger.info("execResult = " + execResult);
                            while(execResult.next())
                            {
                                insertStatement.setLong(1, executionId);
                                insertStatement.setLong(2, test.getId());
                                insertStatement.setString(3, test.getName());
                                insertStatement.setString(4, execResult.getString(1));
                                // execute insert statement
                                insertStatement.executeUpdate();
                            }
                            execResult.close();
                            preparedStatement.close();
                        }
                        else if(part.startsWith("insert")){
                            logger.info("Executing insert statement : " + part);
                            PreparedStatement pt = snomedDataSource.getConnection().prepareStatement(part);
                            logger.info("pt = " + pt);
                            int result = pt.executeUpdate();
                            logger.info("result = " + result);
                        }
                        else {
                            if(part.startsWith("create table") || part.startsWith("drop table")){
                                part = part + " ENGINE = MyISAM";
                            }
                            logger.info("Executing statement :" + part);
//                            boolean result = qaDataSource.getConnection().prepareStatement(part).execute();
                            boolean result = snomedDataSource.getConnection().createStatement().execute(part);
                            if(!result){
                                logger.error("Error executing sql : " + part);
                            }
                        }
                    }

                    // select results that match execution
                    String resultSQL = "select assertion_id, assertion_text, details from " + qaResulTableName + " where assertion_id = ?";
                    PreparedStatement resultStatement = dataSource.getConnection().prepareStatement(resultSQL);
                    resultStatement.setLong(1, test.getId());
                    resultStatement.executeQuery();
                    ResultSet resultSet = resultStatement.executeQuery();
                    String detail = null;
                    int counter = 0;
                    while (resultSet.next())
                    {
                        detail = detail + resultSet.getString(3);
                        counter++;
                    }
                    resultSet.close();
                    resultStatement.close();

                    // if counter is > 0, then we know there are failures
                    if(counter > 0)
                    {
                        runItem.setFailureMessage("Failed Item count : " + counter);
                        runItem.setFailure(true);
                    }
                    else{
                        runItem.setFailure(false);
                    }
                }
                catch (SQLException e) {
                    logger.warn("Nested exception is : " + e.fillInStackTrace());
                    runItem.setFailureMessage("Error executing SQL passed as command object. Nested exception : " + e.fillInStackTrace());
                }
            }
            else {
                logger.warn("Error validating command.: " + command);
                runItem.setFailureMessage("Error validating command.: " + command);
            }
        }
        else{
            throw new IllegalArgumentException("Test passed does not have associated execution command. Test: \n" + test);
        }

        runItem.setRunTime(Calendar.getInstance().getTimeInMillis() - startTime.getTimeInMillis());
        try {
            logger.info("runItem as json = " + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(runItem));
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return runItem;
    }

    @Override
    public Collection<TestRunItem> executeTests(Collection<Test> tests, Long executionId,
                                                String prospectiveReleaseVersion, String previousReleaseVersion) {
        Collection<TestRunItem> items = new ArrayList<>();
        for(Test test: tests){
            items.add(executeTest(test, executionId, prospectiveReleaseVersion, previousReleaseVersion));
        }

        return items;
    }

    @Override
    public String loadSnomedData(String versionName, boolean purgeExisting, File zipDataFile){

        Calendar startTime = Calendar.getInstance();
        String createdSchemaName = "rvf_int_"+versionName;
        try
        {
            boolean alreadyExists = false;
            // first verify if database with name already exists, if it does then we skip
            ResultSet catalogs = snomedDataSource.getConnection().getMetaData().getCatalogs();
            while(catalogs.next())
            {
                String schemaName = catalogs.getString(1);
                if((createdSchemaName).equals(schemaName)){
                    alreadyExists = true;
                    break;
                }
            }
            catalogs.close();

            if(alreadyExists && !purgeExisting){
                return createdSchemaName;
            }

            // get file from jar and write to tmp directory, so we can prepend sql statements and set default schema
            File file1 = new File(AssertionExecutionServiceImpl.class.getResource("/sql/create-tables-mysql.sql").getFile());
            File outputFolder = new File(FileUtils.getTempDirectoryPath(), "scripts_"+versionName);
            logger.info("Setting output folder location = " + outputFolder.getAbsolutePath());
            if(! outputFolder.exists() && outputFolder.isDirectory()) {
                outputFolder.mkdir();
            } else {
                logger.info("Output folder already exists");
            }
            File outputFile = new File(outputFolder.getAbsolutePath(), "create-tables-mysql.sql");
            // add scheme information
            FileUtils.writeStringToFile(outputFile, "drop database if exists rvf_int_"+versionName+";\n", true);
            FileUtils.writeStringToFile(outputFile, "create database if not exists rvf_int_"+versionName+";\n", true);
            FileUtils.writeStringToFile(outputFile, "use rvf_int_"+versionName+";\n", true);
            FileUtils.writeLines(outputFile, FileUtils.readLines(file1), true);

            File file2 = new File(AssertionExecutionServiceImpl.class.getResource("/sql/load-data-mysql.sql").getFile());
            File outputFile2 = new File(outputFolder.getAbsolutePath(), "load-data-mysql.sql");
            FileUtils.writeStringToFile(outputFile2, "use rvf_int_"+versionName+";\n", true);
            for(String line : FileUtils.readLines(file2))
            {
                // process line and add to output file
                line = line.replaceAll("<release_version>", versionName);
                line = line.replaceAll("<data_location>", outputFolder.getAbsolutePath());
                FileUtils.writeStringToFile(outputFile2, line, true);
            }

            // extract SNOMED CT content from zip file
            extractZipFile(zipDataFile, outputFolder.getAbsolutePath());

            logger.info("Executing script located at : " + outputFile.getAbsolutePath());
            Connection connection = snomedDataSource.getConnection();
            ScriptRunner runner = new ScriptRunner(connection);
            InputStreamReader reader = new InputStreamReader(new FileInputStream(outputFile));
            runner.runScript(reader);
            reader.close();

            logger.info("Executing script located at : " + outputFile2.getAbsolutePath());
            InputStreamReader reader2 = new InputStreamReader(new FileInputStream(outputFile2));
            runner.runScript(reader2);
            reader2.close();
            connection.close();
        }
        catch (SQLException e) {
            logger.error("Error creating connection to database. Nested exception is : " + e.fillInStackTrace());
        }
        catch (IOException e) {
            logger.error("Unable to read sql file. Nested exception is : " + e.fillInStackTrace());
        }

        logger.info("Finished loading of data in : " + ((Calendar.getInstance().getTimeInMillis() - startTime.getTimeInMillis())/6000) + " minutes.");
        return createdSchemaName;
    }

    protected void extractZipFile(File file, String outputDir){
        try {
            ZipFile zipFile = new ZipFile(file);

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(outputDir,  entry.getName());
                entryDestination.getParentFile().mkdirs();
                if (entry.isDirectory())
                    entryDestination.mkdirs();
                else {
                    InputStream in = zipFile.getInputStream(entry);
                    OutputStream out = new FileOutputStream(entryDestination);
                    IOUtils.copy(in, out);
                    IOUtils.closeQuietly(in);
                    IOUtils.closeQuietly(out);
                }
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            logger.warn("Nested exception is : " + e.fillInStackTrace());
        }
    }

    public void setQaResulTableName(String qaResulTableName) {
        this.qaResulTableName = qaResulTableName;
    }

    public void setAssertionIdColumnName(String assertionIdColumnName) {
        this.assertionIdColumnName = assertionIdColumnName;
    }

    public void setAssertionNameColumnName(String assertionNameColumnName) {
        this.assertionNameColumnName = assertionNameColumnName;
    }

    public void setAssertionDetailsColumnName(String assertionDetailsColumnName) {
        this.assertionDetailsColumnName = assertionDetailsColumnName;
    }

    public void setDeltaTableSuffix(String deltaTableSuffix) {
        this.deltaTableSuffix = deltaTableSuffix;
    }

    public void setSnapshotTableSuffix(String snapshotTableSuffix) {
        this.snapshotTableSuffix = snapshotTableSuffix;
    }

    public void setFullTableSuffix(String fullTableSuffix) {
        this.fullTableSuffix = fullTableSuffix;
    }
}
