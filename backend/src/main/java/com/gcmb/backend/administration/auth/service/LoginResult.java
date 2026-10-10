package com.gcmb.backend.administration.auth.service;

import com.gcmb.backend.administration.auth.dto.response.Identity;

// Internal result: the session token must only be sent through an HttpOnly cookie.
public record LoginResult(Identity user, String token, long seconds) {}
