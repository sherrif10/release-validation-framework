package org.ihtsdo.rvf.validation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.rvf.entity.FailureDetail;
import org.ihtsdo.rvf.entity.TestRunItem;
import org.ihtsdo.rvf.entity.TestType;
import org.ihtsdo.rvf.entity.ValidationReport;
import org.ihtsdo.rvf.validation.impl.CsvMetadataResultFormatter;
import org.ihtsdo.rvf.validation.impl.StreamTestReport;
import org.ihtsdo.rvf.validation.log.ValidationLog;
import org.ihtsdo.rvf.validation.log.ValidationLogFactory;
import org.ihtsdo.rvf.validation.model.ManifestFile;
import org.ihtsdo.rvf.validation.resource.ResourceProvider;
import org.ihtsdo.rvf.validation.resource.ZipFileResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StructuralTestRunner {
	
	private final Logger logger = LoggerFactory.getLogger(StructuralTestRunner.class);
	
	protected String reportFolderLocation;
	
	@Value("${rvf.test.report.folder.location}")
	protected File reportDataFolder;
	
	@Value("${rvf.validation.failure.threshold}")
	protected int failureThreshold;
	
	private String structureTestReportPath;

	@Autowired
	private ValidationLogFactory validationLogFactory;
	

	public TestReportable execute(final ResourceProvider resourceManager, final PrintWriter writer, final boolean writeSuccesses,
			final ManifestFile manifest) {
		// the information for the manifest testing
		long start = System.currentTimeMillis();
		final StreamTestReport testReport = new StreamTestReport(new CsvMetadataResultFormatter(), writer, writeSuccesses);
		final ValidationLog validationLog = validationLogFactory.getValidationLog(ColumnPatternTester.class);
		// run manifest tests
		if ( manifest != null) {
			runManifestTests(resourceManager, testReport, manifest, validationLogFactory.getValidationLog(ManifestPatternTester.class));
			testReport.addNewLine();
		}
		runColumnTests(resourceManager, testReport, validationLog);
		runLineFeedTests(resourceManager, testReport);

		if ( manifest != null) {
			runRF2FileReleaseTypeTester(testReport, manifest);
		}
		
		testReport.getResult();
		final String summary = testReport.writeSummary();
		validationLog.info(summary);
		logger.debug(("Time taken for structure validation:" + (System.currentTimeMillis() - start)));
		return testReport;
	}

	private void runLineFeedTests(ResourceProvider resourceManager, StreamTestReport testReport) {
		
		final RF2FileStructureTester lineFeedPatternTest = new RF2FileStructureTester(validationLogFactory.getValidationLog(RF2FileStructureTester.class), 
				resourceManager, testReport);
		lineFeedPatternTest.runTests();
		
	}

	public TestReportable execute(final ResourceProvider resourceManager, final PrintWriter writer, final boolean writeSuccesses) {
		return execute(resourceManager, writer, writeSuccesses, null);
	}

	private void runManifestTests(final ResourceProvider resourceManager, final TestReportable report,
			final ManifestFile manifest, final ValidationLog validationLog) {
		final ManifestPatternTester manifestPatternTester = new ManifestPatternTester(validationLog, resourceManager, manifest, report);
		manifestPatternTester.runTests();
	}

	private void runRF2FileReleaseTypeTester(final TestReportable report, final ManifestFile manifest) {
		final RF2FilesReleaseTypeTester rf2FilesReleaseTypeTester = new RF2FilesReleaseTypeTester(
				validationLogFactory.getValidationLog(RF2FilesReleaseTypeTester.class), manifest, report);
		rf2FilesReleaseTypeTester.runTest();
	}

	private void runColumnTests(final ResourceProvider resourceManager, final TestReportable report, final ValidationLog validationLog) {

		final ColumnPatternTester columnPatternTest = new ColumnPatternTester(validationLog, resourceManager, report);
		columnPatternTest.runTests();
	}
	
	public boolean verifyZipFileStructure(final ValidationReport validationReport, final File tempFile, final Long runId, final File manifestFile,
										  final boolean writeSucceses, final String urlPrefix, String storageLocation, Integer maxFailuresExport ) throws IOException {
		boolean isFailed = false;
		final long timeStart = System.currentTimeMillis();
		if (tempFile != null) {
			logger.debug("Start verifying zip file structure of {} against manifest", tempFile.getAbsolutePath());
		}
		// convert groups which is passed as string to assertion groups
		// set up the response in order to stream directly to the response
		final File structureTestReport = new File(getReportDataFolder(), "structure_validation_"+ runId+".txt");
		structureTestReportPath = structureTestReport.getAbsolutePath();
		try (PrintWriter writer = new PrintWriter(structureTestReport)) {
			final ResourceProvider resourceManager = new ZipFileResourceProvider(tempFile);

			TestReportable report;

			if (manifestFile == null) {
				report = execute(resourceManager, writer, writeSucceses,null);
			} else {
				File tempManifestFile  = null;
				try {
					final ManifestFile mf = new ManifestFile(manifestFile);
					report = execute(resourceManager, writer, writeSucceses, mf);
				} finally {
					FileUtils.deleteQuietly(tempManifestFile);
				}
			}
			report.getResult();
			logger.info(report.writeSummary());
			// verify if manifest is valid
			if(report.getNumErrors() > 0) {
				validationReport.setReportUrl(urlPrefix + "/result/structure/" + runId + "?storageLocation=" + storageLocation);
				logger.error("No Errors expected but got " + report.getNumErrors() + " errors");
				logger.info("reportPhysicalUrl : " + structureTestReport.getAbsolutePath());
				// pass file name without extension - we add this back when we retrieve using controller
				logger.info("report.getNumErrors() = " + report.getNumErrors());
				logger.info("report.getNumTestRuns() = " + report.getNumTestRuns());
				final double threshold = report.getNumErrors() / report.getNumTestRuns();
				logger.info("threshold = " + threshold);
				
				// Extract failed tests to report instead showing URL only which link to an physical test result
				extractFailedTestsToReport(validationReport, report, maxFailuresExport);
				
				// bail out only if number of test failures exceeds threshold
				if(threshold > getFailureThreshold()){
					isFailed = true;
				}
			}
		}
		final long timeEnd = System.currentTimeMillis();
		validationReport.addTimeTaken((timeEnd-timeStart)/1000);
		logger.debug("Finished verifying zip file structure of {} against manifest", tempFile.getName());
		return isFailed;
	}

	private void extractFailedTestsToReport(final ValidationReport validationReport, TestReportable report, Integer maxFailuresExport) {
		List<StructuralTestRunItem> structuralTestFailItems = report.getFailedItems();
		Map<String,List<FailureDetail>> structuralTestFailItemMap = new HashMap<>();
		for(StructuralTestRunItem structuralTestRunItem : structuralTestFailItems){
			List<FailureDetail> failDetailList;
			if(structuralTestFailItemMap.containsKey(structuralTestRunItem.getFileName())){
				failDetailList = structuralTestFailItemMap.get(structuralTestRunItem.getFileName());
			} else {
				failDetailList = new ArrayList<>();
			}
			String failMsg = "Structural test " + structuralTestRunItem.getTestType() +
					" failed at row-column " + structuralTestRunItem.getExecutionId() +
					" with error: " + structuralTestRunItem.getActualExpectedValue();
			FailureDetail testFailItem  = new FailureDetail("",failMsg);
			failDetailList.add(testFailItem);
			structuralTestFailItemMap.put(structuralTestRunItem.getFileName(), failDetailList);
		}
		
		if(!structuralTestFailItemMap.isEmpty()){
			List<TestRunItem> testRunFailItems = new ArrayList<>();
			int firstNInstance = maxFailuresExport != null ? maxFailuresExport : 10;
			for(String key : structuralTestFailItemMap.keySet()){
				List<FailureDetail> failItems = structuralTestFailItemMap.get(key);
				
				TestRunItem item = new TestRunItem();
				item.setTestCategory(key);
				item.setTestType(TestType.ARCHIVE_STRUCTURAL);
				item.setFirstNInstances(failItems.stream().limit(firstNInstance).collect(Collectors.toList()));
				item.setFailureCount((long) failItems.size());
				testRunFailItems.add(item);
			}
			validationReport.addFailedAssertions(testRunFailItems);
		}
	}

	/**
	 * The init method that sets up the data folder where all reports are stored. This method must always be called
	 * immediately after instantiating this class outside of Spring context.
	 * //todo move to an async process at some time!
	 */
	@PostConstruct
	public void init() throws Exception {
		logger.info("Sct Data Location passed = " + reportFolderLocation);
		if (reportFolderLocation == null || reportFolderLocation.length() == 0) {
			reportFolderLocation = FileUtils.getTempDirectoryPath() + System.getProperty("file.separator") + "rvf-reports";
		}

		reportDataFolder = new File(reportFolderLocation);
		if(!reportDataFolder.exists()){
			if (reportDataFolder.mkdirs()){
				logger.info("Created report folder at : " + reportFolderLocation);
			} else{
				logger.error("Unable to create data folder at path : " + reportFolderLocation);
				throw new IllegalArgumentException("Bailing out because report folder location can not be set to : " + reportFolderLocation);
			}
		}

		logger.info("Using report folder location as :" + reportDataFolder.getAbsolutePath());
	}

	public void setReportFolderLocation(final String reportFolderLocation) {
		this.reportFolderLocation = reportFolderLocation;
	}

	public File getReportDataFolder() {
		return reportDataFolder;
	}

	public String getStructureTestReportFullPath() {
		return this.structureTestReportPath;
	}
	
	public int getFailureThreshold() {
		return failureThreshold;
	}

	public void setFailureThreshold(final int failureThreshold) {
		this.failureThreshold = failureThreshold;
	}
}
