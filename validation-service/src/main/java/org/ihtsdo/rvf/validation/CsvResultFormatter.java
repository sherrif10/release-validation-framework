package org.ihtsdo.rvf.validation;

import java.util.List;

/**
 *
 */
public class CsvResultFormatter implements ResultFormatter {

    @Override
    public String formatResults(List<TestRunItem> failures, List<TestRunItem> testRuns) {
        StringBuilder output = new StringBuilder();

        output.append(headers).append(String.format("%n"));
        for (TestRunItem ti : testRuns) {
            //  output pass/fail, id,
            output.append(String.format("%s, %s, %s, %s, %s, %s, %s, %s, %s%n",
					ti.getFailureMessage(),
					ti.getExecutionId(),
                    ti.getStartDate(), ti.getFileName(), ti.getFilePath(), ti.getColumnName(), ti.getTestType(),
                    ti.getTestPattern(), ti.getActualExpectedValue()));
        }
        return output.toString();
    }

    private static final String headers = "Result, Line-Column, Execution Start, File Name, File Path, Column Name, Test Type, Test Pattern, Failure Details";
}