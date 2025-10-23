package com.salex.telegram.application.services;

import java.util.Map;

public record ModuleBootstrapperResult(Map<String, CommandHandler> commands) {
}
