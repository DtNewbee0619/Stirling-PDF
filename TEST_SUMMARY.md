# Unit Test and JaCoCo Coverage Report Summary

## Overview

This document provides a summary of the unit test implementation and JaCoCo coverage reporting for the Markdown-to-PDF and PDF-to-Markdown conversion functionality in Stirling-PDF.

## Files Implemented

1. **ConvertMarkdownToPdfTest.java**
   - Tests for the `ConvertMarkdownToPdf` controller class
   - Tests include validation for null inputs, non-markdown files, and successful conversions
   - Tests both sanitized and non-sanitized HTML content handling
   - Tests the `TableAttributeProvider` inner class

2. **ConvertPDFToMarkdownTest.java**
   - Tests for the `ConvertPDFToMarkdown` controller class
   - Tests include handling of successful conversions, error responses, and exception scenarios
   - Uses MockedConstruction pattern to test PDFToFile object creation and usage

3. **PDFToFileTest.java**
   - Tests for the `PDFToFile` utility class
   - Tests include content type validation, single file conversion, multiple file handling (with ZIP output)
   - Tests the sanitizeZipFilename method
   - Tests error handling and cleanup on exceptions

4. **FileToPdfTest.java**
   - Tests for the `FileToPdf` utility class
   - Tests HTML file conversion, zip file handling, sanitization options
   - Tests error handling for invalid file formats and process execution failures
   - Tests handling of empty output files

## JaCoCo Configuration

Added JaCoCo plugin to the build.gradle file with the following configuration:

```gradle
jacocoTestReport {
    dependsOn test
    reports {
        xml.required = true
        html.required = true
        csv.required = false
        html.outputLocation = layout.buildDirectory.dir('reports/jacoco/html')
    }
    
    afterEvaluate {
        classDirectories.setFrom(files(classDirectories.files.collect {
            fileTree(dir: it, exclude: [
                    "stirling/software/SPDF/controller/api/converters/*Test*",
                    "stirling/software/SPDF/model/api/converters/*Test*"
            ])
        }))
    }
}

jacocoTestCoverageVerification {
    dependsOn jacocoTestReport
    violationRules {
        rule {
            limit {
                minimum = 0.70
            }
        }
    }
}
```

## Current Status

While the test files have been successfully created, there are still some issues with running the tests:

1. **Mockito Errors**: There are issues with unnecessary stubbings and invalid use of matchers. These would need to be fixed for the tests to run properly.

2. **JaCoCo Report Generation**: JaCoCo reports are not currently being generated due to test failures and possible configuration issues.

## Test Coverage Approach

The tests were written to follow a whitebox testing approach:

1. **Controller Tests**:
   - Test input validation logic
   - Test interaction with utility classes
   - Test error handling and response creation

2. **Utility Tests**:
   - Test file format handling
   - Test conversion logic 
   - Test error scenarios and cleanup operations
   - Test with different input parameters (sanitize on/off, different file types)

3. **Edge Cases**:
   - Null inputs
   - Empty files
   - Invalid file formats
   - Exception handling

## Next Steps

To complete the testing implementation:

1. Fix the current Mockito errors in the test files
2. Improve stability of tests by adjusting mocking approach
3. Update JaCoCo configuration if needed
4. Run tests and generate coverage reports
5. Identify areas with low coverage and implement additional tests