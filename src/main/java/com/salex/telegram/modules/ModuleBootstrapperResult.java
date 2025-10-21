package com.salex.telegram.modules;

import java.util.Map;

public record ModuleBootstrapperResult(Map<String, CommandHandler> commands) {
}
