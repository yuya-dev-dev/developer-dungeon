package jp.yuya.dev.developerdungeon.app.portal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PortalController {
    @GetMapping("/")
    String index() {
        return "title";
    }
}
