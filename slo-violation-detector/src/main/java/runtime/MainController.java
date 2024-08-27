package runtime;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import utility_beans.ApplicationRegistrationFormData;

@RestController
@RequestMapping("/")
public class MainController {

    @GetMapping("/form")
    public String showForm(Model model) {
        model.addAttribute("formData", new ApplicationRegistrationFormData("test","test"));
        return "form";
    }

    @PostMapping("/submitForm")
    public String submitForm(@ModelAttribute ApplicationRegistrationFormData formData) {
        // Process the form data
        // You can access the form data from the formData object
        // For example, formData.getInputField()
        return "result";
    }
}
