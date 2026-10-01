import type { Bucket } from "@google-cloud/storage";
import { getStorage } from "firebase-admin/storage";

export interface ToolStorage {
  save(path: string, content: string | Buffer, contentType?: string): Promise<void>;
  download(path: string): Promise<Buffer>;
  bucket(): Bucket;
}

export function createAdminToolStorage(): ToolStorage {
  const bucket = getStorage().bucket();
  return {
    async save(path, content, contentType) {
      await bucket.file(path).save(content, contentType ? { contentType } : undefined);
    },
    async download(path) {
      const [bytes] = await bucket.file(path).download();
      return bytes;
    },
    bucket() {
      return bucket;
    },
  };
}

export class FakeToolStorage implements ToolStorage {
  readonly files = new Map<string, Buffer>();

  async save(path: string, content: string | Buffer, _contentType?: string): Promise<void> {
    this.files.set(path, Buffer.isBuffer(content) ? content : Buffer.from(content, "utf8"));
  }

  async download(path: string): Promise<Buffer> {
    const file = this.files.get(path);
    if (!file) {
      throw new Error(`Missing storage file: ${path}`);
    }
    return file;
  }

  bucket(): Bucket {
    const files = this.files;
    return {
      file(path: string) {
        return {
          save: async (content: string | Buffer, _opts?: { contentType?: string }) => {
            files.set(path, Buffer.isBuffer(content) ? content : Buffer.from(content, "utf8"));
          },
          download: async () => {
            const data = files.get(path);
            if (!data) {
              throw new Error(`Missing storage file: ${path}`);
            }
            return [data];
          },
        };
      },
    } as unknown as Bucket;
  }
}
