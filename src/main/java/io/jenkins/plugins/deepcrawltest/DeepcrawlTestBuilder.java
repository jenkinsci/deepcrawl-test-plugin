package io.jenkins.plugins.deepcrawltest;

import hudson.Launcher;
import hudson.Proc;
import hudson.Launcher.ProcStarter;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import hudson.util.Secret;
import io.jenkins.cli.shaded.org.apache.commons.io.output.ByteArrayOutputStream;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.AbstractMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;

public class DeepcrawlTestBuilder extends Builder implements SimpleBuildStep {
  private final static String CLI_VERSION = "1.1.2";
  private final static String CLI_DOWNLOAD_URL = "https://github.com/deepcrawl/deepcrawl-test/releases/download/v${cliVersion}/${cliFilename}";
  private final static Map<OperatingSystem, String> CLI_FILENAME = Stream.of(
    new AbstractMap.SimpleEntry<>(OperatingSystem.LINUX, "deepcrawl-test-linux"),
    new AbstractMap.SimpleEntry<>(OperatingSystem.MACOS, "deepcrawl-test-macos"), 
    new AbstractMap.SimpleEntry<>(OperatingSystem.WINDOWS, "deepcrawl-test-win.exe")
  ).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

  private final String testSuiteId;
  private String userKeyId;
  private Secret userKeySecret;
  private boolean startOnly = false;

  @DataBoundConstructor
  public DeepcrawlTestBuilder(String testSuiteId) {
    this.testSuiteId = testSuiteId;
  }

  public String getTestSuiteId() {
    return this.testSuiteId;
  }

  public String getUserKeyId() {
    return this.userKeyId;
  }

  public Secret getUserKeySecret() { 
    return this.userKeySecret;
  }

  public boolean isStartOnly() {
    return this.startOnly;
  }

  @DataBoundSetter
  public void setStartOnly(boolean startOnly) {
    this.startOnly = startOnly || false;
  }

  @DataBoundSetter
  public void setUserKeyId(String userKeyId) {
    this.userKeyId = userKeyId;
  }

  @DataBoundSetter
  public void setUserKeySecret(Secret userKeySecret) {
    this.userKeySecret = userKeySecret;
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {  
    String buildId = run.getId();
    FilePath uniqueWorkspace = workspace.child(buildId);
    OperatingSystem os = this.getOperatingSystem(workspace, launcher);
    FilePath cliFile = this.downloadCLIExecutable(uniqueWorkspace, os);
    String[] command = this.getCommand(cliFile, buildId, env);
    PrintStream logger = listener.getLogger();
    ProcStarter procStarter = launcher.launch();
    Proc process = procStarter.pwd(uniqueWorkspace).cmds(command).quiet(true).stderr(logger).stdout(logger).start();
    int exitCode = process.join();
    if (exitCode != 0) throw new DeepcrawlTestExitException(exitCode);
  }

  private OperatingSystem getOperatingSystem(FilePath workspace, Launcher launcher) throws IOException, InterruptedException {
    if (!launcher.isUnix()) return OperatingSystem.WINDOWS;
    ProcStarter procStarter = launcher.launch();
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    Proc process = procStarter.pwd(workspace).cmds("uname").quiet(true).stdout(outputStream).start();
    process.join();
    String output = outputStream.toString("UTF-8");
    if (output.startsWith("Darwin")) return OperatingSystem.MACOS;
    return OperatingSystem.LINUX;
  }

  private FilePath downloadCLIExecutable(FilePath workspace, OperatingSystem os) throws IOException, InterruptedException {
    String cliFilename = CLI_FILENAME.get(os);
    String cliDownloadUrl = this.getCLIDownloadUrl(cliFilename);
    return this.downloadFile(workspace, cliFilename, cliDownloadUrl);
  }

  private String getCLIDownloadUrl(String cliFilename) {
    return CLI_DOWNLOAD_URL.replace("${cliVersion}", CLI_VERSION).replace("${cliFilename}", cliFilename);
  }

  private FilePath downloadFile(FilePath workspace, String cliFilename, String cliUrl) throws IOException, InterruptedException {
    InputStream in = new URL(cliUrl).openStream();
    FilePath filePath = workspace.child(cliFilename);
    filePath.copyFrom(in);
    filePath.chmod(0755);
    return filePath;
  }

  private String[] getCommand(FilePath cliFile, String buildId, EnvVars env) {
    String userKeyId = this.userKeyId != null && !this.userKeyId.isEmpty() ? this.userKeyId : env.get("DEEPCRAWL_AUTOMATION_HUB_USER_KEY_ID", "");
    String userKeySecret = this.userKeySecret != null && !this.userKeySecret.getPlainText().isEmpty() ? this.userKeySecret.getPlainText() : env.get("DEEPCRAWL_AUTOMATION_HUB_USER_KEY_SECRET", "");
    String cliPath = "./" + cliFile.getName();
    String testSuiteIdArg = String.format("--testSuiteId=%s", this.testSuiteId);
    String userKeyIdArg = String.format("--userKeyId=%s", userKeyId);
    String userKeySecretArg = String.format("--userKeySecret=%s", userKeySecret);
    String ciBuildIdArg = String.format("--ciBuildId=%s", buildId);
    String startOnlyArg = String.format("--startOnly=%b", this.startOnly);
    return new String[]{ cliPath, testSuiteIdArg, userKeyIdArg, userKeySecretArg, ciBuildIdArg, startOnlyArg };
  }

  @Symbol("runAutomationHubBuild")
  @Extension
  public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
    @Override
    public boolean isApplicable(Class<? extends AbstractProject> aClass) {
      return true;
    }

    @Override
    public String getDisplayName() {
      return "Run Deepcrawl Automation Hub Build";
    }
  }
}
