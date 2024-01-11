package slo_violation_detector_engine.generic;

import utility_beans.CharacterizedThread;

public abstract class SLOViolationDetectorSubcomponent {
    public CharacterizedThread.CharacterizedThreadType thread_type;
    public abstract String get_name();
}
