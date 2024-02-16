export interface FirebaseDataConnect {
  newQuery(name: string, variables: Record<string, any>): QueryRef;
}

export interface QueryRef {
  execute(): Promise<QueryResult>;
  subscribe(): QuerySubscription;
}

export interface QuerySubscription {
  addListener(listener: QuerySubscriptionListener): void;
}

export type QuerySubscriptionListener = (result: QueryResult) => void;

export interface QueryResult {
  type: 'QueryResult';
}
