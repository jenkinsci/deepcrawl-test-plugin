package io.jenkins.plugins.deepcrawltest;

import hudson.Launcher;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Locale;
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
  private String userKeySecret;
  private boolean startOnly;

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

  public String getUserKeySecret() {
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
  public void setUserKeySecret(String userKeySecret) {
    this.userKeySecret = userKeySecret;
  }

  @Override
  public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {  
    OperatingSystem os = this.getOperatingSystem();
    String cliFilename = this.downloadCLIExecutable(os);
    PrintStream logger = listener.getLogger();
    this.execCommand(cliFilename, env, logger);
  }

  private OperatingSystem getOperatingSystem() throws OperatingSystemNotSupportedException {
    String osname = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
    if (osname.indexOf("mac") >= 0 || osname.indexOf("darwin") >= 0) return OperatingSystem.MACOS;
    if (osname.indexOf("win") >= 0) return OperatingSystem.WINDOWS;
    if (osname.indexOf("nux") >= 0) return OperatingSystem.LINUX;
    throw new OperatingSystemNotSupportedException();
  }

  private String downloadCLIExecutable(OperatingSystem os) throws IOException {
    String cliFilename = CLI_FILENAME.get(os);
    String cliDownloadUrl = this.getCLIDownloadUrl(cliFilename);
    this.downloadFile(cliDownloadUrl, cliFilename);
    return cliFilename;
  }

  private String getCLIDownloadUrl(String cliFilename) {
    return CLI_DOWNLOAD_URL.replace("${cliVersion}", CLI_VERSION).replace("${cliFilename}", cliFilename);
  }

  private void downloadFile(String cliUrl, String cliFilename) throws IOException {
    InputStream in = new URL(cliUrl).openStream();
    Path path = Paths.get(System.getProperty("user.dir"), cliFilename);
    Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
    path.toFile().setExecutable(true);
  }

  private void execCommand(String cliFilename, EnvVars env, PrintStream logger) throws IOException, InterruptedException {
    String[] command = this.getCommand(cliFilename, env);
    Process process = Runtime.getRuntime().exec(command);
    this.forwardInputStream(process.getErrorStream(), logger);
    this.forwardInputStream(process.getInputStream(), logger);
    int exitCode = process.waitFor();
    if (exitCode != 0) throw new DeepcrawlTestExitException(exitCode);
  }

  private String[] getCommand(String cliFilename, EnvVars env) {
    String userKeyId = this.userKeyId != null && !this.userKeyId.isEmpty() ? this.userKeyId : env.get("DEEPCRAWL_AUTOMATION_HUB_USER_KEY_ID", "");
    String userKeySecret = this.userKeySecret != null && !this.userKeySecret.isEmpty() ? this.userKeySecret : env.get("DEEPCRAWL_AUTOMATION_HUB_USER_KEY_SECRET", "");
    String cliPath = System.getProperty("user.dir") + "/" + cliFilename;
    String testSuiteIdArg = String.format("--testSuiteId=%s", this.testSuiteId);
    String userKeyIdArg = String.format("--userKeyId=%s", userKeyId);
    String userKeySecretArg = String.format("--userKeySecret=%s", userKeySecret);
    return new String[]{ cliPath, testSuiteIdArg, userKeyIdArg, userKeySecretArg };
  }

  private void forwardInputStream(InputStream inputStream, PrintStream logger) {
    new Thread(new Runnable() {
      public void run() {
        try {
          BufferedReader errorStream = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
          String line;
          while ((line = errorStream.readLine()) != null) logger.println(line);
          errorStream.close();
        } catch (IOException e) {
          logger.println(e.getMessage());
        }
      }
    }).start();
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
