/*
 *
 *  *   HORTONWORKS DATAPLANE SERVICE AND ITS CONSTITUENT SERVICES
 *  *
 *  *   (c) 2016-2018 Hortonworks, Inc. All rights reserved.
 *  *
 *  *   This code is provided to you pursuant to your written agreement with Hortonworks, which may be the terms of the
 *  *   Affero General Public License version 3 (AGPLv3), or pursuant to a written agreement with a third party authorized
 *  *   to distribute this code.  If you do not have a written agreement with Hortonworks or with an authorized and
 *  *   properly licensed third party, you do not have any rights to this code.
 *  *
 *  *   If this code is provided to you under the terms of the AGPLv3:
 *  *   (A) HORTONWORKS PROVIDES THIS CODE TO YOU WITHOUT WARRANTIES OF ANY KIND;
 *  *   (B) HORTONWORKS DISCLAIMS ANY AND ALL EXPRESS AND IMPLIED WARRANTIES WITH RESPECT TO THIS CODE, INCLUDING BUT NOT
 *  *     LIMITED TO IMPLIED WARRANTIES OF TITLE, NON-INFRINGEMENT, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE;
 *  *   (C) HORTONWORKS IS NOT LIABLE TO YOU, AND WILL NOT DEFEND, INDEMNIFY, OR HOLD YOU HARMLESS FOR ANY CLAIMS ARISING
 *  *     FROM OR RELATED TO THE CODE; AND
 *  *   (D) WITH RESPECT TO YOUR EXERCISE OF ANY RIGHTS GRANTED TO YOU FOR THE CODE, HORTONWORKS IS NOT LIABLE FOR ANY
 *  *     DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, PUNITIVE OR CONSEQUENTIAL DAMAGES INCLUDING, BUT NOT LIMITED TO,
 *  *     DAMAGES RELATED TO LOST REVENUE, LOST PROFITS, LOSS OF INCOME, LOSS OF BUSINESS ADVANTAGE OR UNAVAILABILITY,
 *  *     OR LOSS OR CORRUPTION OF DATA.
 *
 */

package com.hortonworks.dataplane.gateway.service;

import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;


@Service
public class SetBackedBlackListCache implements BlacklistCache {

  Logger log = LoggerFactory.getLogger(SetBackedBlackListCache.class);

  //Construct a delay queue
  Set<String> tokens = Sets.newConcurrentHashSet();
  private DelayQueue<DelayToken> delayQueue = new DelayQueue<DelayToken>();


  @Scheduled(fixedRate = 10000)
  private void pollDelayQueue() {
    log.debug("Polling queue for expired tokens");
    Set<DelayToken> delayTokens = Sets.newHashSet();
    delayQueue.drainTo(delayTokens);
    delayTokens.forEach(t -> {
        log.info("Token expired, purging");
        tokens.remove(t.token);

    });
  }


  @Override
  public void blackList(String token,DateTime expiry){
    log.info("Adding" + token+" to blacklist , expires at "+expiry.toString());
    tokens.add(token);
    //add to delay queue
    DelayToken delayToken = new DelayToken(token, expiry);
    delayQueue.offer(delayToken);
  }

  @Override
  public boolean isBacklisted(String token){
    return tokens.contains(token);
  }


  /**
   * Container for delayed objects
   */
  private class DelayToken implements Delayed {

    private String token;
    private DateTime expiry;


    public DelayToken(String token,DateTime expiry) {
      this.token = token;
      this.expiry = expiry;
    }

    @Override
    public long getDelay(TimeUnit unit) {
      return unit.convert(expiry.toInstant().getMillis() - System.currentTimeMillis(),TimeUnit.MILLISECONDS);
    }

    @Override
    public int compareTo(Delayed o) {
      return Ints.saturatedCast(this.expiry.toInstant().getMillis() - ((DelayToken)o).expiry.toInstant().getMillis());
    }
  }
}
