package com.app.sumpyo_be.global.health;

import com.app.sumpyo_be.global.common.PublicApi;
import com.app.sumpyo_be.global.common.BaseResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "StartTestAPI", description = "세팅 후 테스트 api")
public class HealthCheckController {

    @PublicApi
    @GetMapping("/api/health")
    public BaseResponse<String> healthCheck() {
        return BaseResponse.success("서버가 정상적으로 동작 중입니다.", "OK");
    }
}
