#!/usr/bin/env bash
# =====================================================================
# Banco XYZ - certificados de DESARROLLO para los BFF (HTTPS y mTLS)
#
#   ./generar-certificados.sh        (requiere openssl y keytool del JDK)
#
# Genera:
#   ca/banco-xyz-dev-ca.crt          CA que firma los certificados de servidor de los BFF
#                                    (importarla en el navegador/Postman para confiar en HTTPS)
#   ca/cajeros-ca.crt                CA que firma los certificados de los CAJEROS (clientes mTLS)
#   bff-web.p12, bff-movil.p12, bff-cajero.p12   keystores de servidor (se copian a cada BFF)
#   cajeros-truststore.p12           confianza del bff-cajero: SOLO acepta cajeros de esa CA
#   cajero-ATM-STGO-0001.{crt,key,p12}           identidad de un cajero de prueba
#
# En produccion (AWS) los certificados de servidor los emite ACM y los de los cajeros una
# PKI privada (AWS Private CA). Estas claves son solo para desarrollo local.
# =====================================================================
set -euo pipefail
cd "$(dirname "$0")"
PASS="${CERT_PASSWORD:-bancoxyz2026}"
DIAS=825
mkdir -p ca

echo "== CA de servidores (Banco XYZ Dev CA)"
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -sha256 \
  -keyout ca/banco-xyz-dev-ca.key -out ca/banco-xyz-dev-ca.crt \
  -subj "/C=CL/O=Banco XYZ/OU=Desarrollo/CN=Banco XYZ Dev CA" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

for bff in bff-web bff-movil bff-cajero; do
  echo "== Certificado de servidor $bff"
  openssl req -newkey rsa:2048 -nodes -sha256 -keyout "$bff.key" -out "$bff.csr" \
    -subj "/C=CL/O=Banco XYZ/OU=BFF/CN=$bff"
  openssl x509 -req -in "$bff.csr" -CA ca/banco-xyz-dev-ca.crt -CAkey ca/banco-xyz-dev-ca.key -CAcreateserial \
    -days $DIAS -sha256 -out "$bff.crt" -extfile <(printf "%s\n" \
      "subjectAltName=DNS:localhost,DNS:$bff,IP:127.0.0.1" \
      "basicConstraints=CA:FALSE" "keyUsage=critical,digitalSignature,keyEncipherment" "extendedKeyUsage=serverAuth")
  openssl pkcs12 -export -name "$bff" -inkey "$bff.key" -in "$bff.crt" -certfile ca/banco-xyz-dev-ca.crt \
    -out "$bff.p12" -passout pass:"$PASS"
  rm -f "$bff.csr" "$bff.key" "$bff.crt"
done

echo "== CA de cajeros automaticos"
openssl req -x509 -newkey rsa:2048 -nodes -days 3650 -sha256 \
  -keyout ca/cajeros-ca.key -out ca/cajeros-ca.crt \
  -subj "/C=CL/O=Banco XYZ/OU=Red de Cajeros/CN=Banco XYZ Cajeros CA" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign"

TERMINAL=ATM-STGO-0001
echo "== Certificado del cajero $TERMINAL (cliente mTLS)"
openssl req -newkey rsa:2048 -nodes -sha256 -keyout "cajero-$TERMINAL.key" -out "cajero-$TERMINAL.csr" \
  -subj "/C=CL/O=Banco XYZ/OU=Cajeros/CN=$TERMINAL"
openssl x509 -req -in "cajero-$TERMINAL.csr" -CA ca/cajeros-ca.crt -CAkey ca/cajeros-ca.key -CAcreateserial \
  -days $DIAS -sha256 -out "cajero-$TERMINAL.crt" -extfile <(printf "%s\n" \
    "basicConstraints=CA:FALSE" "keyUsage=critical,digitalSignature" "extendedKeyUsage=clientAuth")
openssl pkcs12 -export -name "$TERMINAL" -inkey "cajero-$TERMINAL.key" -in "cajero-$TERMINAL.crt" \
  -out "cajero-$TERMINAL.p12" -passout pass:"$PASS"
rm -f "cajero-$TERMINAL.csr"

echo "== Truststore del bff-cajero (solo la CA de cajeros)"
rm -f cajeros-truststore.p12
keytool -importcert -noprompt -alias cajeros-ca -file ca/cajeros-ca.crt \
  -keystore cajeros-truststore.p12 -storetype PKCS12 -storepass "$PASS"

echo "== Copiando keystores a cada BFF"
for bff in bff-web bff-movil bff-cajero; do
  mv "$bff.p12" "../$bff/src/main/resources/$bff.p12"
done
mv cajeros-truststore.p12 ../bff-cajero/src/main/resources/cajeros-truststore.p12
rm -f ca/*.srl
echo "Listo. Clave de los .p12: $PASS"
