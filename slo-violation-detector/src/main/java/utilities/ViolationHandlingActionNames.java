package utilities;

public enum ViolationHandlingActionNames {
    consult_threshold_and_change,
    consult_threshold_and_do_not_change,
    //delayed_consult_threshold_and_change,
    //delayed_consult_threshold_and_do_not_change, This is more or less equivalent to dropping the reconfiguration, as if a reconfiguration will happen the next time interval it would happen anyway, otherwise it would not happen
    drop_reconfiguration_and_do_not_change,
    drop_reconfiguration_and_change,
    send_reconfiguration_and_do_not_change,
    send_reconfiguration_and_change
}

    