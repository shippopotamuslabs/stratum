import * as admin from "firebase-admin";
import { startImport } from "./import/startImport";
import { getImportJobStatus } from "./import/getImportJobStatus";
import { processImportJob } from "./import/processImportJob";
import { inferCustomLifts } from "./import/inferCustomLifts";
import { inferImportEquipment } from "./import/inferImportEquipment";
import { swapProgramLifts } from "./program-swap/swapProgramLifts";
import { nameGeneratedProgram } from "./program-naming/nameGeneratedProgram";

admin.initializeApp();

export {
  startImport,
  getImportJobStatus,
  processImportJob,
  inferCustomLifts,
  inferImportEquipment,
  swapProgramLifts,
  nameGeneratedProgram,
};
