/*
 * Copyright (c) 2026 Niko Köbler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dasniko.testcontainers.keycloak;

import lombok.Value;

/**
 * Holds a parsed OAuth2 token response.
 * {@code idToken} and {@code refreshToken} may be {@code null} (e.g. for client credentials grants).
 */
@Value
public class TokenResponse {
    String accessToken;
    String idToken;
    String refreshToken;
    int expiresIn;
    String tokenType;
}
