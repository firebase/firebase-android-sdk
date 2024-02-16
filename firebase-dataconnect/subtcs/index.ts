import { assertThat } from './asserts';
import {
  FirebaseDataConnect,
  QuerySubscriptionListener,
  QueryResult
} from './dataconnect';
import * as testutil from './util';

declare const dataConnect: FirebaseDataConnect;
declare const listener: QuerySubscriptionListener;
declare const listener1: QuerySubscriptionListener;
declare const listener2: QuerySubscriptionListener;
declare const result1: QueryResult;
declare const result2: QueryResult;

// Adding a listener should execute the query
async function testNAFOPIQNRKLSJDF() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  const subscription = query.subscribe();

  subscription.addListener(listener);

  await assertThat(listener).isEventuallyNotifiedWithSomeResult();
}

// Executing a query should notify a subscription
async function testNAFIOUSDFJKL() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  await testutil.setQueryResult(query, result1);
  const subscription = query.subscribe();
  subscription.addListener(listener);
  await assertThat(listener).isEventuallyNotifiedWithResult(result1);

  await testutil.setQueryResult(query, result2);
  await query.execute();

  await assertThat(listener).isEventuallyNotifiedWithResult(result2);
}

// A newly-added listener should be immediately notified of the most recent
// result
async function testNASIOPFHSLKDJFKL() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  await testutil.setQueryResult(query, result1);

  const subscription = query.subscribe();
  subscription.addListener(listener1);
  await assertThat(listener1).isEventuallyNotifiedWithResult(result1);

  await testutil.setQueryResult(query, result2);
  subscription.addListener(listener2);
  assertThat(listener2).isImmediatelyNotifiedWithResult(result1);
}

// A newly-added listener should trigger query execution
async function testKLANFOIPSNA() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  await testutil.setQueryResult(query, result1);
  const subscription = query.subscribe();
  subscription.addListener(listener1);
  await assertThat(listener1).isEventuallyNotifiedWithResult(result1);

  await testutil.setQueryResult(query, result2);
  subscription.addListener(listener2);

  await assertThat(listener1).isEventuallyNotifiedWithResult(result2);
  await assertThat(listener2).isEventuallyNotifiedWithResult(result2);
}

// Adding a listener to a new subscription should be immediately notified of
// most recent result from another live subscription
async function testLKAMNFOIPSDF() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  await testutil.setQueryResult(query, result1);
  const subscription1 = query.subscribe();
  const subscription2 = query.subscribe();
  subscription1.addListener(listener1);
  await assertThat(listener1).isEventuallyNotifiedWithResult(result1);

  await testutil.setQueryResult(query, result2);
  subscription2.addListener(listener2);

  assertThat(listener2).isImmediatelyNotifiedWithResult(result1);
}

// Adding a listener to a new subscription should trigger query execution and
// deliver the result to both subscriptions
async function testALFKLJSDFKLJ() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  await testutil.setQueryResult(query, result1);
  const subscription1 = query.subscribe();
  const subscription2 = query.subscribe();
  subscription1.addListener(listener1);
  await assertThat(listener1).isEventuallyNotifiedWithResult(result1);

  await testutil.setQueryResult(query, result2);
  subscription2.addListener(listener2);

  await assertThat(listener1).isEventuallyNotifiedWithResult(result2);
  await assertThat(listener2).isEventuallyNotifiedWithResult(result2);
}
