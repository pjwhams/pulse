/* Copyright 2017 Zutubi Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zutubi.pulse.core.postprocessors.cunit;

import com.zutubi.pulse.core.commands.api.DirectoryArtifactConfiguration;
import com.zutubi.pulse.core.commands.api.FileArtifactConfiguration;
import com.zutubi.pulse.core.commands.api.FileSystemArtifactConfigurationSupport;
import com.zutubi.pulse.core.commands.core.ExamplesBuilder;
import com.zutubi.tove.config.api.ConfigurationExample;

/**
 * Example configurations of cunit.pp.
 */
public class CUnitReportPostProcessorConfigurationExamples
{
    private static final String NAME = "cunit.pp";

    public ConfigurationExample getSingleReport()
    {
        FileArtifactConfiguration output = new FileArtifactConfiguration();
        output.setName("cunit xml report");
        output.setFile("build/reports/CUnit.xml");
        output.setFailIfNotPresent(false);

        return buildProject(output);
    }

    public ConfigurationExample getReportDir()
    {
        DirectoryArtifactConfiguration output = new DirectoryArtifactConfiguration();
        output.setName("test reports");
        output.setBase("${base.dir}/reports");
        output.getInclusions().add("**/*.xml");
        output.setFailIfNotPresent(false);

        return buildProject(output);
    }

    private ConfigurationExample buildProject(FileSystemArtifactConfigurationSupport output)
    {
        CUnitReportPostProcessorConfiguration processor = new CUnitReportPostProcessorConfiguration();
        processor.setName(NAME);
        output.addPostProcessor(processor);

        return ExamplesBuilder.buildProjectForCaptureProcessor("make", output);
    }
}