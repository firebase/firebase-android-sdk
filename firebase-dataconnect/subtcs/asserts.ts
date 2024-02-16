import { QuerySubscriptionListener, QueryResult } from './dataconnect';

export declare function assertThat(
  listener: QuerySubscriptionListener
): QuerySubscriptionListenerAssertions;

export interface QuerySubscriptionListenerAssertions {
  isEventuallyNotifiedWithSomeResult(): Promise<void>;
  isEventuallyNotifiedWithResult(result: QueryResult): Promise<void>;
}
