/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.parameters;

import com.google.common.collect.ImmutableSet;
import de.arbeitsagentur.opdt.keycloak.cassandra.authSession.CassandraAuthSessionProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.client.CassandraClientProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.clientScope.CassandraClientScopeProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.group.CassandraGroupProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.identityProvider.CassandraIdentityProviderStorageProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.loginFailure.CassandraLoginFailureProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.realm.CassandraRealmsProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.role.CassandraRoleProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.Config;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.KeycloakModelParameters;
import de.arbeitsagentur.opdt.keycloak.cassandra.testsuite.NullDeviceRepresentationProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.user.CassandraUserProviderFactory;
import de.arbeitsagentur.opdt.keycloak.cassandra.userSession.CassandraUserSessionProviderFactory;
import de.arbeitsagentur.opdt.keycloak.compatibility.HardcodedDeploymentStateProviderFactory;
import de.arbeitsagentur.opdt.keycloak.compatibility.TransientPublicKeyStorageProviderFactory;
import java.util.Set;
import org.keycloak.cache.DefaultLocalCacheProviderFactory;
import org.keycloak.cache.LocalCacheSPI;
import org.keycloak.credential.CredentialSpi;
import org.keycloak.credential.OTPCredentialProviderFactory;
import org.keycloak.credential.PasswordCredentialProviderFactory;
import org.keycloak.credential.hash.PasswordHashSpi;
import org.keycloak.credential.hash.Pbkdf2Sha256PasswordHashProviderFactory;
import org.keycloak.credential.hash.Pbkdf2Sha512PasswordHashProviderFactory;
import org.keycloak.device.DeviceRepresentationSpi;
import org.keycloak.keys.*;
import org.keycloak.models.IdentityProviderStorageSpi;
import org.keycloak.models.PasswordPolicy;
import org.keycloak.models.SingleUseObjectProviderFactory;
import org.keycloak.models.SingleUseObjectSpi;
import org.keycloak.policy.*;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;
import org.keycloak.services.clientpolicy.ClientPolicyManagerSpi;
import org.keycloak.services.clientpolicy.DefaultClientPolicyManagerFactory;
import org.keycloak.services.clientregistration.policy.ClientRegistrationPolicySpi;
import org.keycloak.services.clientregistration.policy.impl.ClientDisabledClientRegistrationPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.ClientScopesClientRegistrationPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.ConsentRequiredClientRegistrationPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.MaxClientsClientRegistrationPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.ProtocolMappersClientRegistrationPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.RegistrationWebOriginsPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.ScopeClientRegistrationPolicyFactory;
import org.keycloak.services.clientregistration.policy.impl.TrustedHostClientRegistrationPolicyFactory;
import org.keycloak.sessions.AuthenticationSessionSpi;
import org.keycloak.tracing.NoopTracingProviderFactory;
import org.keycloak.tracing.TracingSpi;
import org.keycloak.userprofile.DeclarativeUserProfileProviderFactory;
import org.keycloak.userprofile.UserProfileSpi;
import org.keycloak.userprofile.validator.*;
import org.keycloak.validate.ValidatorFactory;
import org.keycloak.validate.ValidatorSPI;

/**
 * @author hmlnarik
 */
public class Map extends KeycloakModelParameters {

    static final Set<Class<? extends Spi>> ALLOWED_SPIS = ImmutableSet.<Class<? extends Spi>>builder()
            .add(AuthenticationSessionSpi.class)
            .add(SingleUseObjectSpi.class)
            .add(PublicKeyStorageSpi.class)
            .add(ClientPolicyManagerSpi.class)
            .add(KeySpi.class)
            .add(ClientRegistrationPolicySpi.class)
            .add(CredentialSpi.class)
            .add(PasswordPolicyManagerSpi.class)
            .add(PasswordHashSpi.class)
            .add(PasswordPolicySpi.class)
            .add(DeviceRepresentationSpi.class)
            .add(UserProfileSpi.class)
            .add(ValidatorSPI.class)
            .add(IdentityProviderStorageSpi.class)
            .add(TracingSpi.class)
            .add(LocalCacheSPI.class)
            .build();

    static final Set<Class<? extends ProviderFactory>> ALLOWED_FACTORIES =
            ImmutableSet.<Class<? extends ProviderFactory>>builder()
                    .add(CassandraClientProviderFactory.class)
                    .add(CassandraClientScopeProviderFactory.class)
                    .add(CassandraGroupProviderFactory.class)
                    .add(CassandraRealmsProviderFactory.class)
                    .add(CassandraRoleProviderFactory.class)
                    .add(CassandraAuthSessionProviderFactory.class)
                    .add(HardcodedDeploymentStateProviderFactory.class)
                    .add(CassandraUserProviderFactory.class)
                    .add(CassandraUserSessionProviderFactory.class)
                    .add(CassandraLoginFailureProviderFactory.class)
                    .add(CassandraIdentityProviderStorageProviderFactory.class)
                    .add(SingleUseObjectProviderFactory.class)
                    .add(TransientPublicKeyStorageProviderFactory.class)
                    .add(DefaultClientPolicyManagerFactory.class)
                    .add(GeneratedAesKeyProviderFactory.class)
                    .add(GeneratedHmacKeyProviderFactory.class)
                    .add(GeneratedEcdsaKeyProviderFactory.class)
                    .add(ImportedRsaEncKeyProviderFactory.class)
                    .add(ImportedRsaKeyProviderFactory.class)
                    .add(GeneratedRsaEncKeyProviderFactory.class)
                    .add(GeneratedRsaKeyProviderFactory.class)
                    .add(ProtocolMappersClientRegistrationPolicyFactory.class)
                    .add(ClientDisabledClientRegistrationPolicyFactory.class)
                    .add(TrustedHostClientRegistrationPolicyFactory.class)
                    .add(ConsentRequiredClientRegistrationPolicyFactory.class)
                    .add(ClientScopesClientRegistrationPolicyFactory.class)
                    .add(ScopeClientRegistrationPolicyFactory.class)
                    .add(MaxClientsClientRegistrationPolicyFactory.class)
                    .add(RegistrationWebOriginsPolicyFactory.class)
                    .add(OTPCredentialProviderFactory.class)
                    .add(PasswordCredentialProviderFactory.class)
                    .add(DefaultPasswordPolicyManagerProviderFactory.class)
                    .add(Pbkdf2Sha256PasswordHashProviderFactory.class)
                    .add(Pbkdf2Sha512PasswordHashProviderFactory.class)
                    .add(HashAlgorithmPasswordPolicyProviderFactory.class)
                    .add(HashIterationsPasswordPolicyProviderFactory.class)
                    .add(HistoryPasswordPolicyProviderFactory.class)
                    .add(ForceExpiredPasswordPolicyProviderFactory.class)
                    .add(NullDeviceRepresentationProviderFactory.class)
                    .add(DeclarativeUserProfileProviderFactory.class)
                    .add(ValidatorFactory.class)
                    .add(NoopTracingProviderFactory.class)
                    .add(DefaultLocalCacheProviderFactory.class)
                    .build();

    public Map() {
        super(ALLOWED_SPIS, ALLOWED_FACTORIES);
    }

    @Override
    public void updateConfig(Config cf) {
        cf.spi("client-policy-manager")
                .defaultProvider("default")
                .spi("password-hashing")
                .provider(Pbkdf2Sha256PasswordHashProviderFactory.ID)
                .provider(Pbkdf2Sha512PasswordHashProviderFactory.ID)
                .spi("password-policy-manager")
                .defaultProvider("default")
                .spi("password-policy")
                .provider(PasswordPolicy.PASSWORD_HISTORY_ID)
                .provider(PasswordPolicy.FORCE_EXPIRED_ID)
                .provider(PasswordPolicy.HASH_ALGORITHM_ID)
                .provider(PasswordPolicy.HASH_ITERATIONS_ID)
                .spi("credential")
                .provider(PasswordCredentialProviderFactory.PROVIDER_ID)
                .provider(OTPCredentialProviderFactory.PROVIDER_ID)
                .spi("keys")
                .provider(GeneratedAesKeyProviderFactory.ID)
                .provider(GeneratedHmacKeyProviderFactory.ID)
                .provider(GeneratedEcdsaKeyProviderFactory.ID)
                .provider(ImportedRsaEncKeyProviderFactory.ID)
                .provider(ImportedRsaKeyProviderFactory.ID)
                .provider(GeneratedRsaEncKeyProviderFactory.ID)
                .provider(GeneratedRsaKeyProviderFactory.ID)
                .spi("client-registration-policy")
                .provider(ProtocolMappersClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ClientDisabledClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(TrustedHostClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ConsentRequiredClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ClientScopesClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(ScopeClientRegistrationPolicyFactory.PROVIDER_ID)
                .provider(MaxClientsClientRegistrationPolicyFactory.PROVIDER_ID)
                .spi(DeviceRepresentationSpi.NAME)
                .defaultProvider(NullDeviceRepresentationProviderFactory.PROVIDER_ID)
                .spi(UserProfileSpi.ID)
                .defaultProvider(DeclarativeUserProfileProviderFactory.ID)
                .spi("validator")
                .provider(BlankAttributeValidator.ID)
                .provider(AttributeRequiredByMetadataValidator.ID)
                .provider(ReadOnlyAttributeUnchangedValidator.ID)
                .provider(DuplicateUsernameValidator.ID)
                .provider(UsernameHasValueValidator.ID)
                .provider(UsernameIDNHomographValidator.ID)
                .provider(UsernameMutationValidator.ID)
                .provider(DuplicateEmailValidator.ID)
                .provider(EmailExistsAsUsernameValidator.ID)
                .provider(RegistrationEmailAsUsernameUsernameValueValidator.ID)
                .provider(RegistrationUsernameExistsValidator.ID)
                .provider(RegistrationEmailAsUsernameEmailValueValidator.ID)
                .provider(BrokeringFederatedUsernameHasValueValidator.ID)
                .provider(ImmutableAttributeValidator.ID)
                .provider(UsernameProhibitedCharactersValidator.ID)
                .provider(PersonNameProhibitedCharactersValidator.ID)
                .provider(MultiValueValidator.ID);
    }
}
