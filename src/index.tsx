import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
  `The package 'p2p-file-transfer' doesn't seem to be linked. Make sure: \n\n` +
  Platform.select({ ios: "- You have run 'pod install'\n", default: '' }) +
  '- You rebuilt the app after installing the package\n' +
  '- You are not using Expo Go\n';

const P2pFileTransfer = NativeModules.P2pFileTransfer
  ? NativeModules.P2pFileTransfer
  : new Proxy(
      {},
      {
        get() {
          throw new Error(LINKING_ERROR);
        },
      }
    );

export function multiply(a: number, b: number): Promise<number> {
  return P2pFileTransfer.multiply(a, b);
}
