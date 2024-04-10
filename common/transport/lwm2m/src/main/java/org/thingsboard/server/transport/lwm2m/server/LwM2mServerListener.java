/**
 * ThingsBoard, Inc. ("COMPANY") CONFIDENTIAL
 *
 * Copyright © 2016-2024 ThingsBoard, Inc. All Rights Reserved.
 *
 * NOTICE: All information contained herein is, and remains
 * the property of ThingsBoard, Inc. and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to ThingsBoard, Inc.
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is strictly forbidden
 * unless prior written permission is obtained from COMPANY.
 *
 * Access to the source code contained herein is hereby forbidden to anyone except current COMPANY employees,
 * managers or contractors who have executed Confidentiality and Non-disclosure agreements
 * explicitly covering such access.
 *
 * The copyright notice above does not evidence any actual or intended publication
 * or disclosure  of  this source code, which includes
 * information that is confidential and/or proprietary, and is a trade secret, of  COMPANY.
 * ANY REPRODUCTION, MODIFICATION, DISTRIBUTION, PUBLIC  PERFORMANCE,
 * OR PUBLIC DISPLAY OF OR THROUGH USE  OF THIS  SOURCE CODE  WITHOUT
 * THE EXPRESS WRITTEN CONSENT OF COMPANY IS STRICTLY PROHIBITED,
 * AND IN VIOLATION OF APPLICABLE LAWS AND INTERNATIONAL TREATIES.
 * THE RECEIPT OR POSSESSION OF THIS SOURCE CODE AND/OR RELATED INFORMATION
 * DOES NOT CONVEY OR IMPLY ANY RIGHTS TO REPRODUCE, DISCLOSE OR DISTRIBUTE ITS CONTENTS,
 * OR TO MANUFACTURE, USE, OR SELL ANYTHING THAT IT  MAY DESCRIBE, IN WHOLE OR IN PART.
 */
package org.thingsboard.server.transport.lwm2m.server;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.leshan.core.node.TimestampedLwM2mNodes;
import org.eclipse.leshan.core.observation.CompositeObservation;
import org.eclipse.leshan.core.observation.Observation;
import org.eclipse.leshan.core.observation.SingleObservation;
import org.eclipse.leshan.core.request.SendRequest;
import org.eclipse.leshan.core.response.ObserveCompositeResponse;
import org.eclipse.leshan.core.response.ObserveResponse;
import org.eclipse.leshan.server.observation.ObservationListener;
import org.eclipse.leshan.server.queue.PresenceListener;
import org.eclipse.leshan.server.registration.Registration;
import org.eclipse.leshan.server.registration.RegistrationListener;
import org.eclipse.leshan.server.registration.RegistrationUpdate;
import org.eclipse.leshan.server.send.SendListener;
import org.thingsboard.server.transport.lwm2m.server.uplink.LwM2mUplinkMsgHandler;

import java.util.Collection;

import static org.thingsboard.server.transport.lwm2m.utils.LwM2MTransportUtil.convertObjectIdToVersionedId;

@Slf4j
public class LwM2mServerListener {

    private final LwM2mUplinkMsgHandler service;

    public LwM2mServerListener(LwM2mUplinkMsgHandler service) {
        this.service = service;
    }

    public final RegistrationListener registrationListener = new RegistrationListener() {
        /**
         * Register – query represented as POST /rd?…
         */
        @Override
        public void registered(Registration registration, Registration previousReg,
                               Collection<Observation> previousObservations) {
            service.onRegistered(registration, previousObservations);
        }

        /**
         * Update – query represented as CoAP POST request for the URL received in response to Register.
         */
        @Override
        public void updated(RegistrationUpdate update, Registration updatedRegistration,
                            Registration previousRegistration) {
            service.updatedReg(updatedRegistration);
        }

        /**
         * De-register (CoAP DELETE) – Sent by the client when a shutdown procedure is initiated.
         */
        @Override
        public void unregistered(Registration registration, Collection<Observation> observations, boolean expired,
                                 Registration newReg) {
            service.unReg(registration, observations);
        }

    };

    public final PresenceListener presenceListener = new PresenceListener() {
        @Override
        public void onSleeping(Registration registration) {
            log.info("[{}] onSleeping", registration.getEndpoint());
            service.onSleepingDev(registration);
        }

        @Override
        public void onAwake(Registration registration) {
            log.info("[{}] onAwake", registration.getEndpoint());
            service.onAwakeDev(registration);
        }
    };

    public final ObservationListener observationListener = new ObservationListener() {

        @Override
        public void cancelled(Observation observation) {
            log.trace("Canceled Observation [RegistrationId:{}: {}].", observation.getRegistrationId(), observation instanceof SingleObservation ?
                    "SingleObservation: " + ((SingleObservation) observation).getPath() :
                    "CompositeObservation: " + ((CompositeObservation) observation).getPaths());
       }

        @Override
        public void onResponse(SingleObservation observation, Registration registration, ObserveResponse response) {
            if (registration != null) {
                service.onUpdateValueAfterReadResponse(registration, convertObjectIdToVersionedId(observation.getPath().toString(), registration), response);
            }
        }

        @Override
        public void onResponse(CompositeObservation observation, Registration registration, ObserveCompositeResponse response) {
            log.trace("Update Composite Observation [{}: {}].", observation.getRegistrationId(), observation.getPaths());
            service.onUpdateValueAfterReadCompositeResponse(registration, response);
        }

        @Override
        public void onError(Observation observation, Registration registration, Exception error) {
            if (error != null) {
                var path = observation instanceof SingleObservation ? "Single Observation Cancel: " + ((SingleObservation) observation).getPath() : "Composite Observation Cancel: " + ((CompositeObservation) observation).getPaths();
                var msgError = path + ": " + error.getMessage();
                log.trace("Unable to handle notification [RegistrationId:{}]: [{}].", observation.getRegistrationId(), msgError);
                service.onErrorObservation(registration, msgError);
            }
        }

        @Override
        public void newObservation(Observation observation, Registration registration) {
            log.trace("Successful start newObservation  [RegistrationId:{}: {}].", observation.getRegistrationId(), observation instanceof SingleObservation ?
                    "Single: " + ((SingleObservation) observation).getPath() :
                    "Composite: " + ((CompositeObservation) observation).getPaths());
        }
    };

    public final SendListener sendListener = new SendListener() {

        @Override
        public void dataReceived(Registration registration, TimestampedLwM2mNodes data, SendRequest request) {
            if (registration != null) {
                service.onUpdateValueWithSendRequest(registration, request);
            }
        }

        @Override
        public void onError(Registration registration, String errorMessage, Exception error) {

        }
    };
}
