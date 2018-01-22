# 1.17
- Combining results by default (generates combined junit, json and pretty cucumber reports)

# 1.16
- New parameter cucumberRunner.test.failure.ignore, ignores test failures while calculating maven run status (thanks @chdavi)

# 1.15
- Requires Java 1.8 from now on
- Cleaning up unnecessary log messages

# 1.14
- Added system property ```cucumberRunner.threadCount``` and environment variable ```THREAD_COUNT``` to the running 
  threads so that users can set up different artefacts for each thread e.g. set up ```n``` number of databases for the
  test run 
  
# 1.13
- Added system property ```cucumberRunner.threadNumber``` and environment variable ```THREAD_NUMBER``` to the running 
  threads so that users can set up different artefacts for each thread e.g. set up ```n``` number of databases for the
  test run 
