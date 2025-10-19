package com.salex.telegram.Bot;

import java.util.Map;

record ModuleBootstrapperResult(ModuleRegistry moduleRegistry,
                                Map<String, CommandHandler> commands) {
}
