package com.salex.telegram.application.modules;

import java.util.Map;

public record ModuleBootstrapperResult(Map<String, CommandHandler> commands) {
}
