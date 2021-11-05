package io.jenkins.plugins.deepcrawltest;

import hudson.AbortException;

public class DeepcrawlTestExitException extends AbortException {
  DeepcrawlTestExitException(int exitCode) {
    super("Deepcrawl Automation Hub CLI exited with code '" + exitCode + "'.");
  }
}
