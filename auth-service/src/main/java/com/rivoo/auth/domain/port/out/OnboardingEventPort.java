package com.rivoo.auth.domain.port.out;

import com.rivoo.auth.domain.model.OnboardingEvent;

public interface OnboardingEventPort {
    OnboardingEvent save(OnboardingEvent event);
}
