package reinforcement_learning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static utility_beans.generic_component_functionality.OutputFormattingPhase.phase_end;
import static utility_beans.generic_component_functionality.OutputFormattingPhase.phase_start;

public class SeverityClassModel {

    private boolean sort_severity_classes;

    private static HashMap<String,ArrayList<SeverityClassModel>> applications_severity_class_models = new HashMap<>();
    private ArrayList<SeverityClass> severity_classes = new ArrayList<>();


    public SeverityClassModel(boolean sort_severity_classes) {
        this.sort_severity_classes = sort_severity_classes;
    }

    public SeverityClassModel(Integer number_of_classes, boolean sort_severity_classes){
        this.sort_severity_classes = sort_severity_classes;
        initialize_severity_classes(number_of_classes);
    }


    public void initialize_severity_classes (Integer number_of_classes){
        double class_interval = 1.0/number_of_classes;
        for (int class_counter=1; class_counter<=number_of_classes; class_counter++){

            severity_classes.add(new SeverityClass(class_interval*(class_counter-1),class_interval*class_counter,false));
        }
        if (sort_severity_classes) {
            severity_classes.sort(Comparator.comparingDouble(SeverityClass::getMinimum_severity_value));
        }
    }

    public void get_severity_class_status(){
        Logger.getGlobal().log(info_logging_level,phase_start("Severity class status",1));
        for (SeverityClass severity_class : severity_classes){
            String message = String.format("The upper and lower bounds for the current severity class are %f and %f respectively, and the current threshold is %f",severity_class.getMinimum_severity_value(), severity_class.getMaximum_severity_value(), severity_class.getAdaptation_threshold().getValue());
            Logger.getGlobal().log(info_logging_level,message);
        }
        Logger.getGlobal().log(info_logging_level,phase_end(3));
    }

    public ArrayList<String> get_severity_class_status_list(){
        ArrayList<String> severity_class_list = new ArrayList<>();
        for (SeverityClass severity_class : severity_classes){
            String severity_class_info = severity_class.getMinimum_severity_value()+","+severity_class.getMaximum_severity_value()+","+severity_class.getAdaptation_threshold().getValue();
            severity_class_list.add(severity_class_info);
        }
        return severity_class_list;
    }


    public SeverityClass get_severity_class(double severity_value_searched_for) {

        int maximum_containing_severity_class_index = severity_classes.size()-1;
        int minimum_containing_severity_class_index = 0;
        int previous_midpoint=0;
        int midpoint = maximum_containing_severity_class_index/2;
        while (midpoint-(severity_classes.size()-1)<=0 && midpoint>=0 && this_was_not_the_midpoint(midpoint,severity_value_searched_for)){
            previous_midpoint = midpoint;
            if (greater_than_midpoint(midpoint,severity_value_searched_for)>0){
                midpoint = max(midpoint+1,midpoint+(maximum_containing_severity_class_index-midpoint)/2);
                minimum_containing_severity_class_index = previous_midpoint;
            }else if (greater_than_midpoint(midpoint,severity_value_searched_for)<0){
                midpoint = min(midpoint-1,midpoint - (midpoint-minimum_containing_severity_class_index)/2);
                maximum_containing_severity_class_index = previous_midpoint;
            }else{
                return severity_classes.get(midpoint);
            }
        }
        if ((midpoint<severity_classes.size()) && (!this_was_not_the_midpoint(midpoint,severity_value_searched_for))) {
            String message = String.format("Found the class that %s belongs to, it is %d",severity_value_searched_for,midpoint);
            Logger.getGlobal().log(warning_logging_level,message);
            return severity_classes.get(midpoint);
        }else{
            String message = String.format("Tried to find the severity class that value %s belongs to but this was not found",severity_value_searched_for);
            Logger.getGlobal().log(warning_logging_level,message);
            return null;
        }
    }

    private int greater_than_midpoint(int midpoint, Double severity_value_searched_for) {
        return severity_value_searched_for.compareTo(severity_classes.get(midpoint).getMinimum_severity_value());
    }

    /**
     * The logic of this method is inverted! It searches if the particular midpoint is NOT the solution. The midpoint that is the solution should indicate a severity class whose maximum and minimum severity levels enclose the particular severity value. This method returns false if the severity value is within the minimum and maximum severity range of the severity class signified by the particular midpoint, and true if it falls outside this range
     * @param midpoint The midpoint is the indicator of the severity class
     * @param severity_value The severity value searched for
     * @return A boolean value indicating whether the midpoint value does not indicate the severity class enclosing the severity value or it does.
     */
    private boolean this_was_not_the_midpoint(int midpoint, double severity_value) {
        try {
            return (severity_value < severity_classes.get(midpoint).getMinimum_severity_value()) || (severity_value >= severity_classes.get(midpoint).getMaximum_severity_value()); //This was the midpoint after all!
        }catch (Exception e){
            Logger.getGlobal().log(severe_logging_level,"There was an error in finding the midpoint - relevant values were "+midpoint+" and "+severity_value+" for midpoint and severity value respectively");
            e.printStackTrace();
            return false;
        }
    }

    public ArrayList<SeverityClass> getSeverity_classes() {
        return severity_classes;
    }

    public void setSeverity_classes(ArrayList<SeverityClass> severity_classes) {
        this.severity_classes = severity_classes;
    }

    public static HashMap<String, ArrayList<SeverityClassModel>> getApplications_severity_class_models() {
        return applications_severity_class_models;
    }

    public static void setApplications_severity_class_models(HashMap<String, ArrayList<SeverityClassModel>> applications_severity_class_models) {
        SeverityClassModel.applications_severity_class_models = applications_severity_class_models;
    }
}
