/*******************************************************************************
 * Copyright (c) 2018 Synopsys, Inc
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Synopsys, Inc - initial implementation and documentation
 *******************************************************************************/
package jenkins.plugins.coverity;

import java.io.IOException;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.tools.ToolInstallation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import jenkins.plugins.coverity.CoverityToolInstallation.CoverityToolInstallationDescriptor;
import jenkins.tasks.SimpleBuildWrapper;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * A simple build wrapper to contribute Coverity tools bin path to the PATH environment variable
 */
public class CoverityEnvBuildWrapper extends SimpleBuildWrapper {
    private final String coverityToolName;
    private String cimInstance;
    private String hostVariable;
    private String portVariable;
    private String usernameVariable;
    private String passwordVariable;

    @DataBoundConstructor
    public CoverityEnvBuildWrapper(String coverityToolName) {
        this.coverityToolName = coverityToolName;
    }

    public String getCoverityToolName() {
        return coverityToolName;
    }

    @DataBoundSetter
    public void setCimInstance(String cimInstance) {
        this.cimInstance = cimInstance;
    }

    public String getCimInstance() {
        return cimInstance;
    }

    @DataBoundSetter
    public void setHostVariable(String hostVariable) {
        this.hostVariable = hostVariable;
    }

    public String getHostVariable() {
        return hostVariable;
    }

    @DataBoundSetter
    public void setPortVariable(String portVariable) {
        this.portVariable = portVariable;
    }

    public String getPortVariable() {
        return portVariable;
    }

    @DataBoundSetter
    public void setUsernameVariable(String usernameVariable) {
        this.usernameVariable = usernameVariable;
    }

    public String getUsernameVariable() {
        return usernameVariable;
    }

    @DataBoundSetter
    public void setPasswordVariable(String passwordVariable) {
        this.passwordVariable = passwordVariable;
    }

    public String getPasswordVariable() {
        return passwordVariable;
    }

    @Override
    public void setUp(Context context, Run<?, ?> build, FilePath workspace, Launcher launcher, TaskListener listener, EnvVars initialEnvironment) throws IOException, InterruptedException {
        ToolInstallation covTools = getCoverityToolInstallation();
        if (covTools == null) {
            throw new IOException("Unable to find Coverity tools installation: [" + coverityToolName +"]. Please configure one for this Jenkins instance.");
        }

        final Computer computer = workspace.toComputer();
        if (computer == null) {
            throw new AbortException("Cannot get Coverity tools installation");
        }

        final Node node = computer.getNode();
        if (node == null) {
            throw new AbortException("Cannot get Coverity tools installation");
        }

        covTools = covTools.translate(node, initialEnvironment, listener);
        final EnvVars covEnvVars = new EnvVars();
        covTools.buildEnvVars(covEnvVars);

        if (StringUtils.isNotEmpty(cimInstance)) {
            // Add environment variables for CIMInstance information such as host, port, username, and password
            final CoverityPublisher.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(CoverityPublisher.DescriptorImpl.class);
            CIMInstance instance = descriptor.getInstance(cimInstance);
            if (instance != null) {
                covEnvVars.put(StringUtils.isNotEmpty(hostVariable) ? hostVariable : "COVERITY_HOST", instance.getHost());
                covEnvVars.put(StringUtils.isNotEmpty(portVariable) ? portVariable : "COVERITY_PORT", String.valueOf(instance.getPort()));
                covEnvVars.put(StringUtils.isNotEmpty(usernameVariable) ? usernameVariable : "COV_USER", instance.getCoverityUser());
                covEnvVars.put(StringUtils.isNotEmpty(passwordVariable) ? passwordVariable : "COVERITY_PASSPHRASE", instance.getCoverityPassword());
            }
        }

        for (Entry<String,String> entry : covEnvVars.entrySet()) {
            context.env(entry.getKey(), entry.getValue());
        }
    }

    public CoverityToolInstallation getCoverityToolInstallation() {
        final DescriptorImpl descriptor = this.getDescriptor();
        if (this.getDescriptor() != null && coverityToolName != null) {
            for (CoverityToolInstallation toolInstallation : descriptor.getInstallations()) {
                if (coverityToolName.equalsIgnoreCase(toolInstallation.getName()))
                    return toolInstallation;
            }
        }

        return null;
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    @Symbol("withCoverityEnv")
    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public boolean isApplicable(AbstractProject<?, ?> item) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Binds Coverity Tool path and Coverity Connection Instance information to Environment Variables";
        }

        public ListBoxModel doFillCoverityToolNameItems() {
            ListBoxModel result = new ListBoxModel();
            for(CoverityToolInstallation toolInstallation : getInstallations()) {
                result.add(toolInstallation.getName());
            }
            return result;
        }

        public CoverityToolInstallation[] getInstallations() {
            final CoverityToolInstallationDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(CoverityToolInstallationDescriptor.class);
            return descriptor.getInstallations();
        }

        public ListBoxModel doFillCimInstanceItems() {
            final CoverityPublisher.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(CoverityPublisher.DescriptorImpl.class);
            ListBoxModel result = new ListBoxModel();

            for (CIMInstance instance : descriptor.getInstances()) {
                result.add(instance.getName());
            }

            return result;
        }
    }
}
