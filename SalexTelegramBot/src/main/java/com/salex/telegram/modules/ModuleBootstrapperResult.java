package com.salex.telegram.modules;

import java.util.Map;

public record ModuleBootstrapperResult(ModuleRegistry moduleRegistry,
                                       Map<String, CommandHandler> commands) {
}
