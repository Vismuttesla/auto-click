package com.click.precisiontrigger.action;

import java.time.Instant;

public record ActionResult(ActionOutcomeState outcomeState, Instant firedAt, String diagnostic) {
}