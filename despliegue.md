# Despliegue en AWS - Banco XYZ

El sistema ya está contenerizado: cada módulo tiene su `Dockerfile` y el `docker-compose.yaml` levanta los 13 contenedores. 

---

## EC2 con Docker Compose

### 1. Security Group
Crear un `banco-xyz-sg` con estas reglas de entrada:

| Puerto | Origen | Uso |
|---|---|---|
| 22 | Mi IP | SSH |
| 9000 | Mi IP | Login con GitHub |
| 8443, 8444, 8445 | 0.0.0.0/0 | BFF web, móvil y cajero |
| 8761, 8090 | Mi IP | Eureka y Kafka UI |

No abrir PostgreSQL (5433), Kafka (9092), Config Server (8888) ni los puertos de los microservicios. La única entrada son los BFF.

### 2. Instancia
- Amazon Linux 2023, **t3.xlarge** (4 vCPU, 16 GB), disco de 30 GB.
- Los 13 contenedores usan cerca de 8 GB, así que 16 GB deja margen.

### 3. Instalar Docker, Compose y Git
```bash
ssh -i <llave>.pem ec2-user@<IP-PUBLICA>

sudo dnf install -y docker git
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -SL https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64 \
     -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

exit    # reconectarse para que aplique el grupo docker
```

### 4. Configurar y levantar
```bash
git clone https://github.com/<usuario>/<repositorio>.git
cd <repositorio>
cp .env.example .env
nano .env     # GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, DB_PASSWORD y AUTH_OPERADORES
```

En la OAuth App de GitHub cambiar:
- Homepage URL: `http://<IP-PUBLICA>:9000`
- Callback URL: `http://<IP-PUBLICA>:9000/login/oauth2/code/github`

Levantar:
```bash
docker compose up -d --build
docker compose ps     # banco-xyz-batch: Exited (0), el resto: healthy
```

> El emisor de los tokens (`iss`) es `http://localhost:9000` en todos los servicios. Es solo un identificador, no hay que cambiarlo por la IP pública.

### 5. Verificar
```bash
docker logs banco-xyz-batch | grep -E "CUADRA|RESUMEN"
```
Desde mi computador:
1. Eureka: `http://<IP-PUBLICA>:8761` (usuario `eureka`, clave `eureka2026`).
2. Login: `http://<IP-PUBLICA>:9000/oauth2/authorization/github` y copiar el `accessToken`.
3. Llamar a un BFF. El certificado fue emitido para `localhost`, por eso se usa `--resolve`:
```bash
curl -s --cacert certificados/ca/banco-xyz-dev-ca.crt \
     --resolve localhost:8443:<IP-PUBLICA> \
     -H "Authorization: Bearer <accessToken>" https://localhost:8443/api/web/inicio
```
Las demás pruebas (móvil, cajero con mTLS, tolerancia a fallos) están en `instrucciones.md`.

### 6. Escalar y apagar
```bash
docker compose up -d --scale ms-cuentas=3 --scale ms-pagos=2 --scale ms-clientes=2
docker compose down     # con -v también borra la base de datos
```
Cuando no se use, detener o terminar la instancia. Una instancia detenida no cobra cómputo, pero el disco sí.

---

## Ajustes para producción

El proyecto usa valores de desarrollo por comodidad. En la nube conviene cambiar:

| Tema | Ajuste |
|---|---|
| Secretos (`hola123`, `eureka2026`, etc.) | Definirlos por variable de entorno desde Secrets Manager |
| `AUTH_OPERADORES` vacío (todos reciben permisos de escritura) | Definir los usuarios autorizados |
| Cliente por defecto (todos se asocian al cliente 1) | Usar `AUTH_CLIENTES_GITHUB` (`login:clienteId`) |
| Llave JWT generada en memoria | Mantener una sola instancia de `auth-server`, o cargar la llave desde un secreto |
| CA de desarrollo | ACM para los servidores y AWS Private CA para los cajeros |
| Puertos publicados al host | Subredes privadas y Security Groups; solo los balanceadores son públicos |
| Una base compartida | Aceptable para la evaluación; en producción, una base o esquema por servicio |
| `/actuator/**` público en `ms-*` | Restringir por Security Group o mover a un puerto interno |