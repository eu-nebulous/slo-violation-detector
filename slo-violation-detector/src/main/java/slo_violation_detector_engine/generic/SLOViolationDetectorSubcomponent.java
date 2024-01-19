package slo_violation_detector_engine.generic;

import utility_beans.generic_component_functionality.CharacterizedThread;

public abstract class SLOViolationDetectorSubcomponent {
    public CharacterizedThread.CharacterizedThreadType thread_type;
    public abstract String get_name();
}
