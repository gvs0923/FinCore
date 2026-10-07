import { setupServer } from "msw/node";
import { handlers } from "./handlers";

/** MSW server for Vitest — intercepts fetch calls at the network level. */
export const server = setupServer(...handlers);
