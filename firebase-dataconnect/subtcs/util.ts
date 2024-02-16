import { QueryRef, QueryResult } from './dataconnect';

export declare function setQueryResult(
  query: QueryRef,
  result: QueryResult
): Promise<void>;
