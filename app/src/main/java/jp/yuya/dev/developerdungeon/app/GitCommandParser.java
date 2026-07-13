package jp.yuya.dev.developerdungeon.app;

import jp.yuya.dev.developerdungeon.contract.GitCommand;
import org.springframework.stereotype.Component;

@Component
class GitCommandParser {
    private final StageRules rules = new StageRules();
    GitCommand parse(String raw) { return rules.parse(rules.definition("STAGE-GIT-01"), raw); }
}
