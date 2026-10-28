import type { components } from '../api/schema';

/** Pagination data of a page; the generated type marks every field optional, but the MES always sends them. */
export type PageMetadata = Required<components['schemas']['PageMetadata']>;

/** A Spring Data page serialized as DTO (`spring.data.web.pageable.serialization-mode: VIA_DTO`). */
export interface Page<T> {
  content: T[];
  page: PageMetadata;
}
