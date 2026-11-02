import {
  setDefaultResultOrder,getDefaultResultOrder
} from 'node:dns';
import {
  setDefaultAutoSelectFamily,getDefaultAutoSelectFamily
} from 'node:net';

/**
 * The user's Windows Node24 IPv4-preferred, single-family fetch returned
 * public GET /health HTTP 200 after default fetch timed out. This adjusts
 * only the running probe process; it never edits Windows adapter settings,
 * server IP addresses, phone OmniGate, or TLS certificate validation.
 *
 * Invoked exclusively for explicit --diagnose and --execute. No retry,
 * redirect-follow, POST, or body exposure is introduced here.
 */
export function configureIpv4PreferredFetch(){
  setDefaultResultOrder('ipv4first');
  setDefaultAutoSelectFamily(false);
  const result={
    dns_result_order:getDefaultResultOrder(),
    auto_select_family:getDefaultAutoSelectFamily()
  };
  if(result.dns_result_order!=='ipv4first'||
      result.auto_select_family!==false)
    throw Error('IP_FAMILY_PREFERENCE_NOT_APPLIED');
  return result;
}
