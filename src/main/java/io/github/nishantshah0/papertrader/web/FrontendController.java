package io.github.nishantshah0.papertrader.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class FrontendController {
    // Explicit UI routes only: API errors and missing assets must remain real 404s.
    @GetMapping({"/terminal", "/terminal/"})
    String terminal() {
        return "forward:/index.html";
    }
}
