/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.ui.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Filip Hrisafov
 * 包装Authentication，细粒度的返回Authentication的部分数据，比如userId、groupIds、TenantId、hasAutority
 *
 * 这里的设计猜想：
 * 为什么不直接从Authentication拿数据？
 * 为了扩展性，如果想要拿其他的信息，自己实现SecurityScope接口，封装
 */
public class FlowableAuthenticationSecurityScope implements SecurityScope {

    protected final Authentication authentication;

    public FlowableAuthenticationSecurityScope(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public String getUserId() {
        return authentication.getName();
    }

    @Override
    public Set<String> getGroupIds() {
        return extractAuthoritiesStartingWith(SecurityUtils.GROUP_PREFIX).collect(Collectors.toSet());
    }

    @Override
    public String getTenantId() {
        return extractAuthoritiesStartingWith(SecurityUtils.TENANT_PREFIX).findFirst().orElse("");
    }

    @Override
    public boolean hasAuthority(String authority) {
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority grantedAuthority : authorities) {
            if (grantedAuthority.getAuthority().equals(authority)) {
                return true;
            }
        }
        return false;
    }

    protected Stream<String> extractAuthoritiesStartingWith(String prefix) {
        return authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(prefix))
                .map(authority -> authority.substring(prefix.length()));
    }
}
