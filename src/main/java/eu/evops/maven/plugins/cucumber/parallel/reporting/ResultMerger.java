package eu.evops.maven.plugins.cucumber.parallel.reporting;

import java.io.File;
import java.util.List;

/**
 * Created by n450777 on 30/03/2016.
 */
public interface ResultMerger {
    File merge(File outputFolder, List<String> paths) throws MergeException;
}
