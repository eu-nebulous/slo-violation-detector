package slo_violation_detector_engine.generic;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class StoppableRunnable implements Runnable {
    private final AtomicBoolean stop_signal = new AtomicBoolean(false);
    public abstract void stop();

    public AtomicBoolean getStop_signal() {
        return stop_signal;
    }
    
}
