import * as admin from "firebase-admin";
import { deleteAccount } from "./account/deleteAccount";
import { startImport } from "./import/startImport";
import { getImportJobStatus } from "./import/getImportJobStatus";
import { processImportJob } from "./import/processImportJob";
import { inferCustomLifts } from "./import/inferCustomLifts";
import { inferImportEquipment } from "./import/inferImportEquipment";
import { swapProgramLifts } from "./program-swap/swapProgramLifts";
import { startProgramTemplateImport } from "./program-import/startProgramTemplateImport";
import { getProgramTemplateJobStatus } from "./program-import/getProgramTemplateJobStatus";
import { processProgramTemplateJob } from "./program-import/processProgramTemplateJob";
import { startProgramSynthesis } from "./program-synthesis/startProgramSynthesis";
import { getProgramSynthesisStatus } from "./program-synthesis/getProgramSynthesisStatus";
import { processProgramSynthesisJob } from "./program-synthesis/processProgramSynthesisJob";
import { onSynthesisJobWritten } from "./program-synthesis/onSynthesisQuota";
import { inferEquipmentIncrements } from "./incrementscan/inferEquipmentIncrements";
import { syncPlayEntitlement } from "./entitlement/syncPlayEntitlement";

admin.initializeApp();

export {
  deleteAccount,
  startImport,
  getImportJobStatus,
  processImportJob,
  inferCustomLifts,
  inferImportEquipment,
  swapProgramLifts,
  startProgramTemplateImport,
  getProgramTemplateJobStatus,
  processProgramTemplateJob,
  startProgramSynthesis,
  getProgramSynthesisStatus,
  processProgramSynthesisJob,
  onSynthesisJobWritten,
  inferEquipmentIncrements,
  syncPlayEntitlement,
};
