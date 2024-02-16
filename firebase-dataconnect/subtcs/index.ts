import { assertThat } from './asserts';
import {
  FirebaseDataConnect,
  QuerySubscriptionListener,
  QueryResult
} from './dataconnect';
import * as testutil from './util';

declare const dataConnect: FirebaseDataConnect;
declare const listener: QuerySubscriptionListener;
declare const result1: QueryResult;
declare const result2: QueryResult;

async function addingAListenerShouldExecuteTheQuery() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  const subscription = query.subscribe();
  subscription.addListener(listener);
  await assertThat(listener).isEventuallyNotifiedWithSomeResult();
}

async function executingAQueryShouldNotifyASubscription() {
  const query = dataConnect.newQuery('GetPersonById', { id: 'foo' });
  await testutil.setQueryResult(query, result1);

  const subscription = query.subscribe();
  subscription.addListener(listener);
  await assertThat(listener).isEventuallyNotifiedWithResult(result1);

  // Change the result of the query, then execute the query.
  await testutil.setQueryResult(query, result2);
  await query.execute();

  // Verify that the subscribed listener gets notified with the updated result.
  await assertThat(listener).isEventuallyNotifiedWithResult(result2);
}
