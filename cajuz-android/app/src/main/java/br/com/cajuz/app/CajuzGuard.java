package br.com.cajuz.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.widget.Toast;

/** Protects the Android app UI. It does NOT replace WordPress authentication. */
final class CajuzGuard {
  static final int DEVICE_CREDENTIAL = 9125;
  private final Activity owner;
  private Runnable onVerified;
  private boolean pending;
  private boolean switchingToPin;

  CajuzGuard(Activity owner) { this.owner=owner; }
  boolean pending() { return pending; }

  void verify(Runnable success) {
    if(pending) return;
    KeyguardManager km=(KeyguardManager)owner.getSystemService(Context.KEYGUARD_SERVICE);
    if(km==null||!km.isDeviceSecure()) {
      new AlertDialog.Builder(owner).setTitle("Proteção do dispositivo")
        .setMessage("Ative um PIN, senha ou biometria nas configurações do Android para proteger o Cajuz.")
        .setPositiveButton("Entendi",null).show();
      return;
    }
    this.onVerified=success;
    this.pending=true;
    this.switchingToPin=false;
    if(Build.VERSION.SDK_INT<28){ credential(); return; }
    try {
      BiometricPrompt prompt=new BiometricPrompt.Builder(owner)
        .setTitle("Cajuz protegido")
        .setSubtitle("Confirme sua identidade para continuar")
        .setNegativeButton("Usar PIN ou senha",owner.getMainExecutor(),(dialog,which)->{
          switchingToPin=true;
          credential();
        })
        .build();
      prompt.authenticate(new CancellationSignal(),owner.getMainExecutor(),
        new BiometricPrompt.AuthenticationCallback(){
          @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result){
            complete(true);
          }
          @Override public void onAuthenticationError(int error,CharSequence message){
            if(switchingToPin)return;
            if(error==13 /* negativo, constante disponível no AndroidX */){
              switchingToPin=true;credential();return;
            }
            if(error==BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED ||
               error==BiometricPrompt.BIOMETRIC_ERROR_CANCELED){complete(false);return;}
            switchingToPin=true;
            credential();
          }
        });
    } catch (Exception ex) { switchingToPin=true;credential(); }
  }

  private void credential() {
    try {
      KeyguardManager km=(KeyguardManager)owner.getSystemService(Context.KEYGUARD_SERVICE);
      Intent verify=km.createConfirmDeviceCredentialIntent("Cajuz protegido","Desbloqueie usando a proteção do Android.");
      if(verify==null)throw new IllegalStateException();
      owner.startActivityForResult(verify,DEVICE_CREDENTIAL);
    } catch(Exception ex) {
      Toast.makeText(owner,"Não foi possível abrir o bloqueio do dispositivo.",Toast.LENGTH_LONG).show();
      complete(false);
    }
  }
  void credentialResult(int resultCode){ complete(resultCode==Activity.RESULT_OK); }
  private void complete(boolean verified){
    pending=false;switchingToPin=false;
    Runnable listener=onVerified;onVerified=null;
    if(verified&&listener!=null) listener.run();
  }
}
