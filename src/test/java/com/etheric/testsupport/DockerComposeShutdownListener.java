package com.etheric.testsupport;

import com.etheric.infrastructure.DockerComposeSupport;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Stops docker-compose after the full test plan finishes (success or failure).
 */
public final class DockerComposeShutdownListener implements TestExecutionListener {

    private static final AtomicBoolean STOPPED = new AtomicBoolean(false);

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (DockerComposeSupport.isSkipped()) {
            return;
        }
        if (!STOPPED.compareAndSet(false, true)) {
            return;
        }
        DockerComposeSupport.downQuiet();
    }
}
