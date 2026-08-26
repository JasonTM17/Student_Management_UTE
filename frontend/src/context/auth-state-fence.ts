export interface AuthStateSnapshot {
  commit(apply: () => void): boolean;
}

export class AuthStateFence {
  private version = 0;

  capture(): AuthStateSnapshot {
    const capturedVersion = this.version;

    return {
      commit: (apply) => {
        if (capturedVersion !== this.version) {
          return false;
        }

        apply();
        return true;
      },
    };
  }

  invalidate(): void {
    this.version += 1;
  }
}
