export const getError = (reasonCode: number, message?: string) => {
  switch (reasonCode) {
    case error.code:
      return error;
    case p2pUnsupported.code:
      return p2pUnsupported;
    case busy.code:
      return busy;
    default:
      return {
        code: reasonCode,
        message: message ?? 'Unknown error.',
      };
  }
};

type Error = {
  code: number;
  message: string;
};

const error: Error = {
  code: 0,
  message: 'Operation failed due to an internal error.',
};

const p2pUnsupported: Error = {
  code: 1,
  message: 'Operation failed because p2p is unsupported on the device.',
};

const busy: Error = {
  code: 2,
  message:
    'Operation failed because the framework is busy and unable to service the request.',
};
