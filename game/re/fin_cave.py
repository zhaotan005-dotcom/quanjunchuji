# v2.98 FinishUsingItemCastTime 修复 cave（SLACK+0x3d0）：
# 0xcdea88 原本 ldr r0,[r4]; ldr r2,[r0,#0x4a8]; mov r0,r4; blx r2  = ClientStopPreUsingItem(CachedPreUsingItem)
# 单机/房主本机人物上这个 Client RPC 在本地执行 → vt+0x4a0 把 inv+0x2c0 清 0 → 后面 0xcdb0ac(inv)==inv+0x2c0 失败。
# cave：照样调（客户端那一半的收尾照做），调完 +0x2c0 被清 0 就写回原值，再回 0xcdea98。
import struct
HOOK=0xcdea88; RET=0xcdea98; ORIG='000094e5a82490e5'
WORDS=[0xe92d0006,0xe5940000,0xe59024a8,0xe1a00004,0xe12fff32,0xe8bd0006,0xe59402c0,0xe3500000,0x058412c0,0xe51ff004]
def code(): return ''.join(struct.pack('<I',w).hex() for w in WORDS)
if __name__=='__main__':
    print(code(), len(WORDS)*4+4)
