package com.app.sumpyo_be.global.action.dto;

import com.app.sumpyo_be.global.action.entity.ActionStatus;

/** PATCH /api/actions/{actionId}/check */
public record ActionStatusResponse(ActionStatus status) {}
