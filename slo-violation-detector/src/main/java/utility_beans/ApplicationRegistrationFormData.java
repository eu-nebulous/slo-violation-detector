package utility_beans;

public class ApplicationRegistrationFormData {
    private String application_name;
    private String application_slo_rule;

    public ApplicationRegistrationFormData(String application_name,String application_slo_rule){
        this.application_name = application_name;
        this.application_slo_rule = application_slo_rule;
    }
    public String getApplication_name() {
        return application_name;
    }

    public void setApplication_name(String application_name) {
        this.application_name = application_name;
    }

    public String getApplication_slo_rule() {
        return application_slo_rule;
    }

    public void setApplication_slo_rule(String application_slo_rule) {
        this.application_slo_rule = application_slo_rule;
    }
}
